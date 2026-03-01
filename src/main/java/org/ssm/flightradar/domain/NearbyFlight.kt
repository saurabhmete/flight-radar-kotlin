package org.ssm.flightradar.domain

data class NearbyFlight(
    val icao24: String,
    val callsign: String,
    val altitude: Double?,
    val lat: Double,
    val lon: Double,
    val velocity: Double?,
    val distanceKm: Double,

    val departure: String?,
    val departureName: String?,
    val departureIata: String?,

    val arrival: String?,
    val arrivalName: String?,
    val arrivalIata: String?,

    val operatorIcao: String?,
    val operatorName: String?,

    val aircraftTypeIcao: String?,
    val aircraftNameShort: String?,
    val aircraftNameFull: String?,

    val aircraftImageUrl: String?,
    val aircraftImageType: AircraftImageType?
)

enum class AircraftImageType {
    EXACT,
    SILHOUETTE
}
