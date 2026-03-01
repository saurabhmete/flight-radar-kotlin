package org.ssm.flightradar.datasource

import org.ssm.flightradar.domain.FlightState

interface OpenSkyDataSource {
    suspend fun getStatesInBoundingBox(
        lamin: Double,
        lomin: Double,
        lamax: Double,
        lomax: Double
    ): List<FlightState>
}
