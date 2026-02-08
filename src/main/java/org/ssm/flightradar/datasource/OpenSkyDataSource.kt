package org.ssm.flightradar.datasource

import kotlinx.serialization.json.JsonObject
import org.ssm.flightradar.domain.FlightState

interface OpenSkyDataSource {
    suspend fun getStatesInBoundingBox(
        lamin: Double,
        lomin: Double,
        lamax: Double,
        lomax: Double
    ): List<FlightState>

    suspend fun getFlightHistoryByCallsign(
        callsign: String,
        beginEpoch: Long,
        endEpoch: Long
    ): List<JsonObject>
}
