package org.ssm.flightradar.datasource

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.domain.FlightState
import java.util.Base64

class OpenSkyClient(private val config: AppConfig) : OpenSkyDataSource {

    private val log = LoggerFactory.getLogger(OpenSkyClient::class.java)

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    // OpenSky now uses HTTP Basic Auth directly on the states endpoint.
    // The old Keycloak OAuth flow (auth.opensky-network.org) is deprecated.
    private val basicAuthHeader: String = run {
        val credentials = "${config.openskyClientId}:${config.openskyClientSecret}"
        "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
    }

    override suspend fun getStatesInBoundingBox(
        lamin: Double,
        lomin: Double,
        lamax: Double,
        lomax: Double
    ): List<FlightState> {
        return try {
            val response: HttpResponse = client.get(
                "https://opensky-network.org/api/states/all" +
                        "?lamin=$lamin&lomin=$lomin&lamax=$lamax&lomax=$lomax"
            ) {
                header(HttpHeaders.Authorization, basicAuthHeader)
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

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val states = when (val el = root["states"]) {
                is JsonArray -> el
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
            states.mapNotNull { el ->
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
        } catch (e: Exception) {
            log.warn("OpenSky states fetch failed: {}", e.message)
            emptyList()
        }
    }
}
