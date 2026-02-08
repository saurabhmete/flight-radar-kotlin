package org.ssm.flightradar.persistence

interface FlightCacheRepository {
    suspend fun getCachedFlight(callsign: String): FlightCacheDocument?

    /**
     * Ensures a cache document exists for the given callsign, and updates lastSeen/cachedAt.
     */
    suspend fun upsertObservation(
        callsign: String,
        icao24: String,
        nowEpoch: Long
    )

    suspend fun updateRoute(
        callsign: String,
        departure: String?,
        arrival: String?,
        routeCheckedAtEpoch: Long,
        routeNotFoundUntilEpoch: Long?
    )

    suspend fun updateAircraftImage(
        callsign: String,
        aircraftImageUrl: String,
        aircraftImageType: String
    )

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
