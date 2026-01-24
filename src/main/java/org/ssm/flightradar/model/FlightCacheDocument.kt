package org.ssm.flightradar.model

import org.bson.types.ObjectId

data class FlightCacheDocument(
    val _id: ObjectId? = null,

    val callsign: String,
    val icao24: String,

    val altitude: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val velocity: Double? = null,

    val departure: String? = null,
    val departure_name: String? = null,

    val arrival: String? = null,
    val arrival_name: String? = null,

    val arrival_retry_count: Int = 0,

    val first_seen: Long,
    val timestamp_cached: Long
)
