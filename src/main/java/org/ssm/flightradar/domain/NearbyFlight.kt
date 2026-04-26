package org.ssm.flightradar.domain

data class NearbyFlight(
    val icao24: String,
    val callsign: String,
    val altitude: Double?,
    val lat: Double,
    val lon: Double,
    val velocity: Double?,
    val trueTrack: Double?,
    val distanceKm: Double,

    val departure: String? = null,
    val departureName: String? = null,
    val departureIata: String? = null,

    val arrival: String? = null,
    val arrivalName: String? = null,
    val arrivalIata: String? = null,

    val operatorIcao: String? = null,
    val operatorName: String? = null,

    val aircraftTypeIcao: String? = null,
    val aircraftNameShort: String? = null,
    val aircraftNameFull: String? = null,

    val aircraftImageUrl: String? = null,
    val aircraftImageType: AircraftImageType? = null
)

enum class AircraftImageType {
    EXACT,
    SILHOUETTE
}
