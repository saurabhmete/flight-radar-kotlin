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
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.model.FlightState

class OpenSkyClient(private val config: AppConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /* =========================
       Auth (Client Credentials)
       ========================= */

    private suspend fun getAccessToken(): String {
        val response = client.submitForm(
            url = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token",
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", config.openskyClientId)
                append("client_secret", config.openskyClientSecret)
            }
        ).body<JsonObject>()

        return response["access_token"]?.jsonPrimitive?.content
            ?: error("OpenSky access_token missing")
    }

    /* =========================
       LIVE STATES (used by API)
       ========================= */

    suspend fun getStatesInBoundingBox(
        lamin: Double,
        lomin: Double,
        lamax: Double,
        lomax: Double
    ): List<FlightState> {

        val token = getAccessToken()

        val responseText = client.get(
            "https://opensky-network.org/api/states/all" +
                    "?lamin=$lamin&lomin=$lomin&lamax=$lamax&lomax=$lomax"
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()

        val root = json.parseToJsonElement(responseText).jsonObject
        val states = root["states"]?.jsonArray ?: return emptyList()

        /*
         OpenSky state array indices (important):
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

    suspend fun getFlightHistoryByCallsign(
        callsign: String,
        beginEpoch: Long,
        endEpoch: Long
    ): List<JsonObject> {

        val token = getAccessToken()

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
