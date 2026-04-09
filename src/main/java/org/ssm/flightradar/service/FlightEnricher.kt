package org.ssm.flightradar.service

import org.ssm.flightradar.domain.NearbyFlight

fun interface FlightEnricher {
    suspend fun enrich(flight: NearbyFlight, nowEpoch: Long): NearbyFlight
}
