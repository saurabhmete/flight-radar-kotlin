package org.ssm.flightradar.service

import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.domain.NearbyFlight
import org.ssm.flightradar.util.Geo
import kotlin.math.sqrt

class FlightService(
    private val openSky: OpenSkyDataSource,
    private val mongo: FlightCacheRepository,
    private val config: AppConfig,
    private val enrichment: FlightEnrichmentService
) {
    private val HOME_LAT = 51.505122562296975
    private val HOME_LON = 7.466314232256936

    private val MAX_DISTANCE_KM = 100.0
    private val MIN_ALTITUDE_METERS = 0
    private val VISIBILITY_FACTOR = 0.25

    private val centerLat = config.centerLat
    private val centerLon = config.centerLon

    suspend fun nearby(
        limit: Int,
    ): List<NearbyFlight> {

        val nowEpoch = System.currentTimeMillis() / 1000

        val states = openSky.getStatesInBoundingBox(
            lamin = centerLat - config.bboxDeltaDeg,
            lomin = centerLon - config.bboxDeltaDeg,
            lamax = centerLat + config.bboxDeltaDeg,
            lomax = centerLon + config.bboxDeltaDeg
        )

        val base = states.mapNotNull { state ->
            val lat = state.lat ?: return@mapNotNull null
            val lon = state.lon ?: return@mapNotNull null

            val distance =
                Geo.haversineKm(HOME_LAT, HOME_LON, lat, lon)

            if (!isVisible(distance, state.altitude)) return@mapNotNull null

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
        altitudeMeters: Double?
    ): Boolean {
        if (altitudeMeters == null) return false
        if (altitudeMeters < MIN_ALTITUDE_METERS) return false
        if (distanceKm > MAX_DISTANCE_KM) return false

        // Distance to horizon (km)
        val horizonKm = 3.57 * sqrt(altitudeMeters)
        val effectiveVisibilityKm = horizonKm * VISIBILITY_FACTOR

        return distanceKm <= effectiveVisibilityKm
    }
}
