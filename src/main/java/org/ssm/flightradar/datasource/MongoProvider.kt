package org.ssm.flightradar.datasource

import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.KMongo
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.persistence.FlightCacheDocument
import org.ssm.flightradar.persistence.FlightCacheRepository

class MongoProvider(config: AppConfig) : FlightCacheRepository {

    private val client = KMongo.createClient(config.mongoUri).coroutine
    private val database = client.getDatabase(config.mongoDb)
    private val flights = database.getCollection<FlightCacheDocument>("flights")

    /* =========================
       Used by REST API
       ========================= */

    override suspend fun getCachedFlight(callsign: String): FlightCacheDocument? {
        return flights.findOne(FlightCacheDocument::callsign eq callsign)
    }

    /* =========================
       Used by arrival batch job
       ========================= */

    override suspend fun findFlightsNeedingArrivalUpdate(
        yesterdayEpoch: Long
    ): List<FlightCacheDocument> {
        return flights.find(
            FlightCacheDocument::arrival eq null,
            FlightCacheDocument::arrivalRetryCount lt 3,
            FlightCacheDocument::firstSeenEpoch lte yesterdayEpoch
        ).toList()
    }

    override suspend fun updateArrival(
        callsign: String,
        arrival: String,
        arrivalName: String?
    ) {
        flights.updateOne(
            FlightCacheDocument::callsign eq callsign,
            combine(
                setValue(FlightCacheDocument::arrival, arrival),
                setValue(FlightCacheDocument::arrivalName, arrivalName)
            )
        )
    }

    override suspend fun incrementArrivalRetry(callsign: String) {
        flights.updateOne(
            FlightCacheDocument::callsign eq callsign,
            inc(FlightCacheDocument::arrivalRetryCount, 1)
        )
    }
}
