package org.ssm.flightradar.datasource

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.domain.FlightState
import java.util.concurrent.atomic.AtomicReference

class OpenSkyClient(private val config: AppConfig) : OpenSkyDataSource {

    private val log = LoggerFactory.getLogger(OpenSkyClient::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /* =========================
       Token state (cached)
       ========================= */

    private val accessToken = AtomicReference<String?>(null)
    private var tokenExpiresAtEpoch: Long = 0L

    /* =========================
       Auth (Client Credentials)
       ========================= */

    private suspend fun ensureAccessToken(): String {
        val now = System.currentTimeMillis() / 1000

        val cached = accessToken.get()
        if (cached != null && now < tokenExpiresAtEpoch - 60) {
            return cached
        }

        val response = client.submitForm(
            url = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token",
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", config.openskyClientId)
                append("client_secret", config.openskyClientSecret)
            }
        ).body<JsonObject>()

        val token = response["access_token"]?.jsonPrimitive?.content
            ?: error("OpenSky access_token missing")

        val expiresIn = response["expires_in"]?.jsonPrimitive?.longOrNull
            ?: error("OpenSky expires_in missing")

        accessToken.set(token)
        tokenExpiresAtEpoch = now + expiresIn

        return token
    }

    /* =========================
       LIVE STATES (used by API)
       ========================= */

    override suspend fun getStatesInBoundingBox(
        lamin: Double,
        lomin: Double,
        lamax: Double,
        lomax: Double
    ): List<FlightState> {

        val token = ensureAccessToken()

        val response: HttpResponse = client.get(
            "https://opensky-network.org/api/states/all" +
                    "?lamin=$lamin&lomin=$lomin&lamax=$lamax&lomax=$lomax"
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status != HttpStatusCode.OK) {
            log.warn("OpenSky states API failed: {}", response.status)
            return emptyList()
        }

        val contentType = response.headers[HttpHeaders.ContentType] ?: ""
        if (!contentType.contains("application/json")) {
            log.warn("OpenSky returned non-JSON response: {}", contentType)
            return emptyList()
        }

        val responseText = response.bodyAsText()
        val root = json.parseToJsonElement(responseText).jsonObject
        val states = when (val statesElement = root["states"]) {
            is JsonArray -> statesElement
            else -> return emptyList()
        }
        /*
         OpenSky state array indices:
         0  -> icao24
         1  -> callsign (padded)
         5  -> longitude
         6  -> latitude
         7  -> baro_altitude
         9  -> velocity
        */

        return states.mapNotNull { el ->
            val arr = el.jsonArray

            val callsignRaw = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val callsign = callsignRaw.trim()
            if (callsign.isBlank()) return@mapNotNull null

            FlightState(
                icao24 = arr[0].jsonPrimitive.content,
                callsign = callsign,
                lon = arr.getOrNull(5)?.jsonPrimitive?.doubleOrNull,
                lat = arr.getOrNull(6)?.jsonPrimitive?.doubleOrNull,
                altitude = arr.getOrNull(7)?.jsonPrimitive?.doubleOrNull,
                velocity = arr.getOrNull(9)?.jsonPrimitive?.doubleOrNull
            )
        }
    }

    /* =====================================
       HISTORICAL FLIGHTS (arrival batch job)
       ===================================== */

    override suspend fun getFlightHistoryByCallsign(
        callsign: String,
        beginEpoch: Long,
        endEpoch: Long
    ): List<JsonObject> {

        val token = ensureAccessToken()

        val responseText = client.get(
            "https://opensky-network.org/api/flights/callsign" +
                    "?callsign=${callsign.trim()}" +
                    "&begin=$beginEpoch" +
                    "&end=$endEpoch"
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()

        val parsed = json.parseToJsonElement(responseText)

        // API returns [] if no data
        if (parsed !is JsonArray) return emptyList()

        return parsed.map { it.jsonObject }
    }
}
