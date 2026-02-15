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

    val altitude: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val velocity: Double? = null,

    @SerialName("distance_km")
    val distanceKm: Double,

    val departure: String? = null,

    @SerialName("departure_name")
    val departureName: String? = null,

    val arrival: String? = null,

    @SerialName("arrival_name")
    val arrivalName: String? = null,

    @SerialName("operator_icao")
    val operatorIcao: String? = null,

    @SerialName("operator_name")
    val operatorName: String? = null,

    @SerialName("aircraft_type_icao")
    val aircraftTypeIcao: String? = null,

    @SerialName("aircraft_name_short")
    val aircraftNameShort: String? = null,

    @SerialName("aircraft_name_full")
    val aircraftNameFull: String? = null,

    @SerialName("aircraft_image_url")
    val aircraftImageUrl: String? = null,

    @SerialName("aircraft_image_type")
    val aircraftImageType: String? = null
)
