package org.ssm.flightradar.service

import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.domain.NearbyFlight
import org.ssm.flightradar.util.Geo

class FlightService(
    private val openSky: OpenSkyDataSource,
    private val mongo: FlightCacheRepository,
    private val config: AppConfig,
    private val enrichment: FlightEnrichmentService
) {

    private val centerLat = config.centerLat
    private val centerLon = config.centerLon

    suspend fun nearby(
        limit: Int,
        maxDistanceKm: Double
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
                Geo.haversineKm(centerLat, centerLon, lat, lon)

            if (distance > maxDistanceKm) return@mapNotNull null

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
}
