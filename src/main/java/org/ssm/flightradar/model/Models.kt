package org.ssm.flightradar.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NearbyFlightsResponse(
    val flights: List<NearbyFlight>
)

@Serializable
data class NearbyFlight(
    val icao24: String,
    val callsign: String,

    val altitude: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val velocity: Double? = null,

    @SerialName("distance_km")
    val distanceKm: Double,

    val departure: String? = null,
    val departure_name: String? = null,

    val arrival: String? = null,
    val arrival_name: String? = null
)

/**
 * Internal model for live OpenSky state
 */
data class FlightState(
    val icao24: String,
    val callsign: String,
    val lat: Double?,
    val lon: Double?,
    val altitude: Double?,
    val velocity: Double?
)
