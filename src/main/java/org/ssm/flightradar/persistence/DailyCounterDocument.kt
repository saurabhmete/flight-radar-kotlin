package org.ssm.flightradar.persistence

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

/**
 * Simple daily counter (used to cap paid API calls).
 */
data class DailyCounterDocument(
    @BsonId
    val id: ObjectId? = null,

    /**
     * Date in UTC, YYYY-MM-DD.
     */
    val date: String,

    val count: Int = 0
)
