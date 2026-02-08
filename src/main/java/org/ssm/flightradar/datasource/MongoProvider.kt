package org.ssm.flightradar.datasource

import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.KMongo
import com.mongodb.client.model.UpdateOptions
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

    override suspend fun upsertObservation(callsign: String, icao24: String, nowEpoch: Long) {
        flights.updateOne(
            filter = FlightCacheDocument::callsign eq callsign,
            update = combine(
                setOnInsert(FlightCacheDocument::callsign, callsign),
                setOnInsert(FlightCacheDocument::icao24, icao24),
                setOnInsert(FlightCacheDocument::firstSeenEpoch, nowEpoch),
                setValue(FlightCacheDocument::lastSeenEpoch, nowEpoch),
                setValue(FlightCacheDocument::cachedAtEpoch, nowEpoch)
            ),
            options = UpdateOptions().upsert(true)
        )
    }

    override suspend fun updateRoute(
        callsign: String,
        departure: String?,
        arrival: String?,
        routeCheckedAtEpoch: Long,
        routeNotFoundUntilEpoch: Long?
    ) {
        flights.updateOne(
            filter = FlightCacheDocument::callsign eq callsign,
            update = combine(
                setValue(FlightCacheDocument::departure, departure),
                setValue(FlightCacheDocument::arrival, arrival),
                setValue(FlightCacheDocument::routeCheckedAtEpoch, routeCheckedAtEpoch),
                setValue(FlightCacheDocument::routeNotFoundUntilEpoch, routeNotFoundUntilEpoch)
            )
        )
    }

    override suspend fun updateAircraftImage(callsign: String, aircraftImageUrl: String, aircraftImageType: String) {
        flights.updateOne(
            filter = FlightCacheDocument::callsign eq callsign,
            update = combine(
                setValue(FlightCacheDocument::aircraftImageUrl, aircraftImageUrl),
                setValue(FlightCacheDocument::aircraftImageType, aircraftImageType)
            )
        )
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
