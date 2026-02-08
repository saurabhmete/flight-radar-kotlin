package org.ssm.flightradar.persistence

import org.bson.types.ObjectId
import org.bson.codecs.pojo.annotations.BsonId

/**
 * MongoDB document used as a lightweight cache to enrich live OpenSky states.
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

    val departure: String? = null,
    val departureName: String? = null,

    val arrival: String? = null,
    val arrivalName: String? = null,

    /**
     * When we last attempted to enrich this flight's route (epoch seconds).
     */
    val routeCheckedAtEpoch: Long? = null,

    /**
     * If route enrichment was not found, we won't retry until this time (epoch seconds).
     */
    val routeNotFoundUntilEpoch: Long? = null,

    /**
     * URL of an aircraft image.
     * - May be a local static asset (e.g. /static/aircraft/plane.svg)
     * - Or an external URL (e.g. Wikimedia Commons), if we ever add exact photos.
     */
    val aircraftImageUrl: String? = null,

    /**
     * EXACT or SILHOUETTE.
     */
    val aircraftImageType: String? = null,

    /**
     * Number of times the arrival batch job attempted to resolve arrival.
     */
    val arrivalRetryCount: Int = 0,

    /**
     * When we first observed this flight (epoch seconds).
     */
    val firstSeenEpoch: Long,

    /**
     * When we last observed this callsign in live data (epoch seconds).
     */
    val lastSeenEpoch: Long? = null,

    /**
     * When this document was last cached/refreshed (epoch seconds).
     */
    val cachedAtEpoch: Long
)
