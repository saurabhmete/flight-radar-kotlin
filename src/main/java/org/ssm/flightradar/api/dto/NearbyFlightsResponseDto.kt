package org.ssm.flightradar.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NearbyFlightsResponseDto(
    val flights: List<NearbyFlightDto>
)
@Serializable
data class NearbyFlightDto(
    val icao24: String,
    val callsign: String,
    val altitude: Double?,
    val lat: Double,
    val lon: Double,
    val velocity: Double?,
    @SerialName("true_track") val trueTrack: Double?,
    @SerialName("distance_km") val distanceKm: Double,

    val departure: String?,
    @SerialName("departure_name") val departureName: String?,
    @SerialName("departure_iata") val departureIata: String?,

    val arrival: String?,
    @SerialName("arrival_name") val arrivalName: String?,
    @SerialName("arrival_iata") val arrivalIata: String?,

    @SerialName("operator_icao") val operatorIcao: String?,
    @SerialName("operator_name") val operatorName: String?,

    @SerialName("aircraft_type_icao") val aircraftTypeIcao: String?,
    @SerialName("aircraft_name_short") val aircraftNameShort: String?,
    @SerialName("aircraft_name_full") val aircraftNameFull: String?,

    @SerialName("aircraft_image_url") val aircraftImageUrl: String?,
    @SerialName("aircraft_image_type") val aircraftImageType: String?
)