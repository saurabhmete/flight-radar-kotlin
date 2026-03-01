package org.ssm.flightradar.datasource

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.ssm.flightradar.config.AppConfig

/**
 * Minimal FlightAware AeroAPI client.
 *
 * We only call GET /flights/{ident}.
 * We parse a minimal subset of fields and treat everything as best-effort.
 */
class AeroApiClient(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(AeroApiClient::class.java)

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Serializable
    data class AeroApiFlightsResponse(
        val flights: List<AeroApiFlight> = emptyList()
    )

    @Serializable
    data class AeroApiFlight(
        val ident: String? = null,

        @SerialName("operator_icao")
        val operatorIcao: String? = null,

        @SerialName("aircraft_type")
        val aircraftType: String? = null,

        val origin: AeroApiAirport? = null,
        val destination: AeroApiAirport? = null
    )

    @Serializable
    data class AeroApiAirport(
        @SerialName("code_icao")
        val codeIcao: String? = null
    )

    data class FlightInfo(
        val operatorIcao: String?,
        val aircraftTypeIcao: String?,
        val originIcao: String?,
        val destinationIcao: String?
    )

    suspend fun fetchFlightInfo(ident: String): FlightInfo? {
        if (config.aeroApiKey.isBlank()) {
            log.warn("AeroAPI key not configured")
            return null
        }

        val url = config.aeroApiBaseUrl.trimEnd('/') + "/flights/${ident.trim()}"

        return try {
            val response = client.get(url) {
                header("x-apikey", config.aeroApiKey)
                accept(ContentType.Application.Json)
            }

            if (response.status != HttpStatusCode.OK) {
                log.debug("AeroAPI /flights failed: status={} ident={}", response.status.value, ident)
                return null
            }

            val body = response.body<AeroApiFlightsResponse>()
            val first = body.flights.firstOrNull() ?: return null

            FlightInfo(
                operatorIcao = first.operatorIcao?.trim()?.takeIf { it.isNotBlank() },
                aircraftTypeIcao = first.aircraftType?.trim()?.takeIf { it.isNotBlank() },
                originIcao = first.origin?.codeIcao?.trim()?.takeIf { it.isNotBlank() },
                destinationIcao = first.destination?.codeIcao?.trim()?.takeIf { it.isNotBlank() }
            )
        } catch (t: Throwable) {
            log.debug("AeroAPI fetch failed ident={}", ident, t)
            null
        }
    }
}
