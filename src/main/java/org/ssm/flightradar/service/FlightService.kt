package org.ssm.flightradar.service

import org.ssm.flightradar.datasource.MongoProvider
import org.ssm.flightradar.datasource.OpenSkyClient
import org.ssm.flightradar.model.NearbyFlight
import org.ssm.flightradar.util.Geo

class FlightService(
    private val openSky: OpenSkyClient,
    private val mongo: MongoProvider
) {

    // Dortmund center
    private val centerLat = 51.5136
    private val centerLon = 7.4653

    suspend fun nearby(
        limit: Int,
        maxDistanceKm: Double
    ): List<NearbyFlight> {

        val states = openSky.getStatesInBoundingBox(
            lamin = centerLat - 1.0,
            lomin = centerLon - 1.0,
            lamax = centerLat + 1.0,
            lomax = centerLon + 1.0
        )

        return states.mapNotNull { state ->
            val lat = state.lat ?: return@mapNotNull null
            val lon = state.lon ?: return@mapNotNull null

            val distance =
                Geo.haversineKm(centerLat, centerLon, lat, lon)

            if (distance > maxDistanceKm) return@mapNotNull null

            val cached = mongo.getCachedFlight(state.callsign)

            NearbyFlight(
                icao24 = state.icao24,
                callsign = state.callsign,

                altitude = state.altitude,
                lat = lat,
                lon = lon,
                velocity = state.velocity,

                distanceKm = distance,

                departure = cached?.departure,
                departure_name = cached?.departure_name,

                arrival = cached?.arrival,
                arrival_name = cached?.arrival_name
            )
        }
            .sortedBy { it.distanceKm }
            .take(limit)
    }
}
