package org.ssm.flightradar.service.enrichment

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.ssm.flightradar.datasource.OpenSkyDataSource

/**
 * Best-effort route enrichment using OpenSky "flights/callsign" endpoint.
 *
 * Notes:
 * - Live-day OpenSky history can be incomplete; this returns null if no confident match is found.
 * - This class does NOT implement caching; cache decisions are handled by [FlightEnrichmentService].
 */
class RouteEnricher(
    private val openSky: OpenSkyDataSource
) {

    data class Route(val departure: String, val arrival: String)

    suspend fun resolveRouteBestEffort(
        callsign: String,
        beginEpoch: Long,
        endEpoch: Long
    ): Route? {
        val flights: List<JsonObject> = openSky.getFlightHistoryByCallsign(
            callsign = callsign,
            beginEpoch = beginEpoch,
            endEpoch = endEpoch
        )

        if (flights.isEmpty()) return null

        // We prefer the most recent flight with both airports known.
        val sorted = flights.sortedByDescending { it["lastSeen"]?.jsonPrimitive?.longOrNull ?: 0L }

        for (f in sorted) {
            val dep = f["estDepartureAirport"]?.jsonPrimitive?.contentOrNull
            val arr = f["estArrivalAirport"]?.jsonPrimitive?.contentOrNull

            if (!dep.isNullOrBlank() && !arr.isNullOrBlank()) {
                return Route(dep, arr)
            }
        }

        return null
    }
}
