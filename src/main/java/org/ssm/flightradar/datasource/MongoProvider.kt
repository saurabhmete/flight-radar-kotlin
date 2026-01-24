package org.ssm.flightradar.datasource

import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.KMongo
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.model.FlightCacheDocument

class MongoProvider(config: AppConfig) {

    private val client = KMongo.createClient(config.mongoUri).coroutine
    private val database = client.getDatabase("flight_radar")
    private val flights = database.getCollection<FlightCacheDocument>("flights")

    /* =========================
       Used by REST API
       ========================= */

    suspend fun getCachedFlight(callsign: String): FlightCacheDocument? {
        return flights.findOne(FlightCacheDocument::callsign eq callsign)
    }

    /* =========================
       Used by arrival batch job
       ========================= */

    suspend fun findFlightsNeedingArrivalUpdate(
        yesterdayEpoch: Long
    ): List<FlightCacheDocument> {
        return flights.find(
            FlightCacheDocument::arrival eq null,
            FlightCacheDocument::arrival_retry_count lt 3,
            FlightCacheDocument::first_seen lte yesterdayEpoch
        ).toList()
    }

    suspend fun updateArrival(
        callsign: String,
        arrival: String,
        arrivalName: String?
    ) {
        flights.updateOne(
            FlightCacheDocument::callsign eq callsign,
            combine(
                setValue(FlightCacheDocument::arrival, arrival),
                setValue(FlightCacheDocument::arrival_name, arrivalName)
            )
        )
    }

    suspend fun incrementArrivalRetry(callsign: String) {
        flights.updateOne(
            FlightCacheDocument::callsign eq callsign,
            inc(FlightCacheDocument::arrival_retry_count, 1)
        )
    }
}
