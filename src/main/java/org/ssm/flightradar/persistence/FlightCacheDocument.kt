package org.ssm.flightradar.persistence

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

/**
 * MongoDB document used as a lightweight cache to enrich live OpenSky states.
 *
 * IMPORTANT:
 * - We cache both successes and failures (negative cache) to keep AeroAPI cost under control.
 * - This is a cache, not a source of truth.
 */
data class FlightCacheDocument(
    @BsonId
    val id: ObjectId? = null,

    val callsign: String,
    val icao24: String,

    val altitude: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val velocity: Double? = null,

    // Route (ICAO)
    val departureIcao: String? = null,
    val arrivalIcao: String? = null,

    // Operator / Aircraft codes
    val operatorIcao: String? = null,
    val aircraftTypeIcao: String? = null,

    // Human-friendly names (via public CDN best-effort)
    val operatorName: String? = null,
    val aircraftNameShort: String? = null,
    val aircraftNameFull: String? = null,

    /**
     * Enrichment attempts for this callsign via AeroAPI.
     */
    val aeroApiAttemptCount: Int = 0,

    /**
     * If AeroAPI didn't yield data, we won't retry until this time (epoch seconds).
     */
    val aeroApiNotFoundUntilEpoch: Long? = null,

    /**
     * When we last attempted AeroAPI enrichment (epoch seconds).
     */
    val aeroApiCheckedAtEpoch: Long? = null,

    /**
     * When this document was first created (epoch seconds).
     */
    val firstSeenEpoch: Long,

    /**
     * When we last observed this callsign in live data (epoch seconds).
     */
    val lastSeenEpoch: Long? = null,

    /**
     * When this document was last cached/refreshed (epoch seconds).
     */
    val cachedAtEpoch: Long,

    /**
     * Aircraft image URL and type.
     */
    val aircraftImageUrl: String? = null,
    val aircraftImageType: String? = null
)
