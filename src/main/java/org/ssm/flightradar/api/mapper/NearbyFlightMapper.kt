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
    distanceKm = distanceKm,
    departure = departure,
    departureName = departureName,
    arrival = arrival,
    arrivalName = arrivalName,
    aircraftImageUrl = aircraftImageUrl,
    aircraftImageType = aircraftImageType?.name
)
