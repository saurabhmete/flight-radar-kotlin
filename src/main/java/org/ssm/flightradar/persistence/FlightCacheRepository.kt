package org.ssm.flightradar.persistence

interface FlightCacheRepository {
    suspend fun getCachedFlight(callsign: String): FlightCacheDocument?

    suspend fun findFlightsNeedingArrivalUpdate(
        yesterdayEpoch: Long
    ): List<FlightCacheDocument>

    suspend fun updateArrival(
        callsign: String,
        arrival: String,
        arrivalName: String?
    )

    suspend fun incrementArrivalRetry(callsign: String)
}
