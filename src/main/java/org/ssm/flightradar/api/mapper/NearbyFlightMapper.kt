package org.ssm.flightradar.api.mapper

import org.ssm.flightradar.api.dto.NearbyFlightDto
import org.ssm.flightradar.domain.NearbyFlight

fun NearbyFlight.toDto(): NearbyFlightDto = NearbyFlightDto(
    icao24 = icao24,
    callsign = callsign,
    altitude = altitude,
    lat = lat,
    lon = lon,
    velocity = velocity,
    trueTrack = trueTrack,
    distanceKm = distanceKm,

    departure = departure,
    departureName = departureName,
    departureIata = departureIata,

    arrival = arrival,
    arrivalName = arrivalName,
    arrivalIata = arrivalIata,

    operatorIcao = operatorIcao,
    operatorName = operatorName,

    aircraftTypeIcao = aircraftTypeIcao,
    aircraftNameShort = aircraftNameShort,
    aircraftNameFull = aircraftNameFull,

    aircraftImageUrl = aircraftImageUrl,
    aircraftImageType = aircraftImageType?.name
)