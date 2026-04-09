package org.ssm.flightradar.service

import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.domain.NearbyFlight
import org.ssm.flightradar.util.Geo
import kotlin.math.sqrt

class FlightService(
    private val openSky: OpenSkyDataSource,
    private val config: AppConfig,
    private val enrichment: FlightEnricher
) {
    private val MIN_ALTITUDE_METERS = 500.0
    private val VISIBILITY_FACTOR = 1.0

    suspend fun nearby(
        limit: Int,
        maxDistanceKm: Double
    ): List<NearbyFlight> {

        val nowEpoch = System.currentTimeMillis() / 1000

        val states = openSky.getStatesInBoundingBox(
            lamin = config.centerLat - config.bboxDeltaDeg,
            lomin = config.centerLon - config.bboxDeltaDeg,
            lamax = config.centerLat + config.bboxDeltaDeg,
            lomax = config.centerLon + config.bboxDeltaDeg
        )

        val base = states.mapNotNull { state ->
            val lat = state.lat ?: return@mapNotNull null
            val lon = state.lon ?: return@mapNotNull null

            val distance = Geo.haversineKm(config.homeLat, config.homeLon, lat, lon)

            if (!isVisible(distance, state.altitude, maxDistanceKm)) return@mapNotNull null

            NearbyFlight(
                icao24 = state.icao24,
                callsign = state.callsign,
                altitude = state.altitude,
                lat = lat,
                lon = lon,
                velocity = state.velocity,
                distanceKm = distance
            )
        }
            .sortedBy { it.distanceKm }
            .take(limit)

        // Enrich only the limited set (avoid unnecessary external calls).
        return base.map { enrichment.enrich(it, nowEpoch) }
    }

    private fun isVisible(
        distanceKm: Double,
        altitudeMeters: Double?,
        maxDistanceKm: Double
    ): Boolean {
        if (altitudeMeters == null) return false
        if (altitudeMeters < MIN_ALTITUDE_METERS) return false
        if (distanceKm > maxDistanceKm) return false

        // Distance to horizon (km)
        val horizonKm = 3.57 * sqrt(altitudeMeters)
        val effectiveVisibilityKm = horizonKm * VISIBILITY_FACTOR

        return distanceKm <= effectiveVisibilityKm
    }
}
