package org.ssm.flightradar.persistence

import org.ssm.flightradar.domain.AircraftImageType

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

    suspend fun updateAircraftImage(
        callsign: String,
        aircraftImageUrl: String,
        aircraftImageType: AircraftImageType
    )

    /**
     * Best-effort update of enriched metadata.
     */
    suspend fun updateEnrichment(
        callsign: String,
        departureIcao: String?,
        arrivalIcao: String?,
        operatorIcao: String?,
        aircraftTypeIcao: String?,
        operatorName: String?,
        aircraftNameShort: String?,
        aircraftNameFull: String?,
        aeroApiCheckedAtEpoch: Long?,
        aeroApiNotFoundUntilEpoch: Long?,
        aeroApiAttemptCountDelta: Int
    )

    /**
     * Attempts to reserve one paid AeroAPI call for today. Returns true if allowed.
     *
     * This is the main cost guardrail.
     */
    suspend fun tryAcquireAeroApiSlot(
        utcDate: String,
        maxPerDay: Int
    ): Boolean
}
