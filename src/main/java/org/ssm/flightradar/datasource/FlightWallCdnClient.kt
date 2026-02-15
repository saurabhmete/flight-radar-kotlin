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
 * Best-effort lookups for human-friendly names.
 *
 * Uses AxisNimble/TheFlightWall public CDN layout:
 * - /oss/lookup/airline/{ICAO}.json -> { display_name_full }
 * - /oss/lookup/aircraft/{ICAO}.json -> { display_name_short, display_name_full }
 */
class FlightWallCdnClient(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(FlightWallCdnClient::class.java)

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Serializable
    data class AirlineLookup(
        @SerialName("display_name_full")
        val displayNameFull: String? = null
    )

    @Serializable
    data class AircraftLookup(
        @SerialName("display_name_short")
        val displayNameShort: String? = null,
        @SerialName("display_name_full")
        val displayNameFull: String? = null
    )

    suspend fun lookupAirlineNameFull(operatorIcao: String): String? {
        val code = operatorIcao.trim().uppercase()
        if (code.isBlank()) return null

        val url = config.flightWallCdnBaseUrl.trimEnd('/') + "/oss/lookup/airline/${code}.json"

        return try {
            val resp = client.get(url) { accept(ContentType.Application.Json) }
            if (resp.status != HttpStatusCode.OK) return null
            resp.body<AirlineLookup>().displayNameFull?.trim()?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            log.debug("CDN airline lookup failed code={}", code, t)
            null
        }
    }

    suspend fun lookupAircraftNames(aircraftTypeIcao: String): Pair<String?, String?> {
        val code = aircraftTypeIcao.trim().uppercase()
        if (code.isBlank()) return null to null

        val url = config.flightWallCdnBaseUrl.trimEnd('/') + "/oss/lookup/aircraft/${code}.json"

        return try {
            val resp = client.get(url) { accept(ContentType.Application.Json) }
            if (resp.status != HttpStatusCode.OK) return null to null

            val dto = resp.body<AircraftLookup>()
            val short = dto.displayNameShort?.trim()?.takeIf { it.isNotBlank() }
            val full = dto.displayNameFull?.trim()?.takeIf { it.isNotBlank() }
            short to full
        } catch (t: Throwable) {
            log.debug("CDN aircraft lookup failed code={}", code, t)
            null to null
        }
    }
}
