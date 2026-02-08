package org.ssm.flightradar.domain

data class NearbyFlight(
    val icao24: String,
    val callsign: String,

    val altitude: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val velocity: Double? = null,

    val distanceKm: Double,

    val departure: String? = null,
    val departureName: String? = null,

    val arrival: String? = null,
    val arrivalName: String? = null
)
