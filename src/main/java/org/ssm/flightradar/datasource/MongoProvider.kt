package org.ssm.flightradar.datasource

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.UpdateOptions
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.KMongo
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.domain.AircraftImageType
import org.ssm.flightradar.persistence.DailyCounterDocument
import org.ssm.flightradar.persistence.FlightCacheDocument
import org.ssm.flightradar.persistence.FlightCacheRepository

class MongoProvider(config: AppConfig) : FlightCacheRepository {

    private val client = KMongo.createClient(config.mongoUri).coroutine
    private val database = client.getDatabase(config.mongoDb)

    private val flights = database.getCollection<FlightCacheDocument>("flights")
    private val daily = database.getCollection<DailyCounterDocument>("daily_counters")

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

    override suspend fun updateAircraftImage(
        callsign: String,
        aircraftImageUrl: String,
        aircraftImageType: AircraftImageType
    ) {
        flights.updateOne(
            FlightCacheDocument::callsign eq callsign,
            combine(
                setValue(FlightCacheDocument::aircraftImageUrl, aircraftImageUrl),
                setValue(FlightCacheDocument::aircraftImageType, aircraftImageType.name)
            )
        )
    }

    override suspend fun updateEnrichment(
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
    ) {
        val updates = mutableListOf<Bson>()

        if (departureIcao != null) updates += setValue(FlightCacheDocument::departureIcao, departureIcao)
        if (arrivalIcao != null) updates += setValue(FlightCacheDocument::arrivalIcao, arrivalIcao)

        if (operatorIcao != null) updates += setValue(FlightCacheDocument::operatorIcao, operatorIcao)
        if (aircraftTypeIcao != null) updates += setValue(FlightCacheDocument::aircraftTypeIcao, aircraftTypeIcao)

        if (operatorName != null) updates += setValue(FlightCacheDocument::operatorName, operatorName)
        if (aircraftNameShort != null) updates += setValue(FlightCacheDocument::aircraftNameShort, aircraftNameShort)
        if (aircraftNameFull != null) updates += setValue(FlightCacheDocument::aircraftNameFull, aircraftNameFull)

        if (aeroApiCheckedAtEpoch != null) updates += setValue(FlightCacheDocument::aeroApiCheckedAtEpoch, aeroApiCheckedAtEpoch)
        if (aeroApiNotFoundUntilEpoch != null) updates += setValue(FlightCacheDocument::aeroApiNotFoundUntilEpoch, aeroApiNotFoundUntilEpoch)

        if (aeroApiAttemptCountDelta != 0) updates += inc(FlightCacheDocument::aeroApiAttemptCount, aeroApiAttemptCountDelta)

        if (updates.isEmpty()) return

        flights.updateOne(
            filter = FlightCacheDocument::callsign eq callsign,
            update = combine(updates)
        )
    }

    override suspend fun tryAcquireAeroApiSlot(
        utcDate: String,
        maxPerDay: Int
    ): Boolean {

        val result = daily.findOneAndUpdate(
            filter = and(
                DailyCounterDocument::date eq utcDate,
                DailyCounterDocument::count lt maxPerDay
            ),
            update = combine(
                setOnInsert(DailyCounterDocument::date, utcDate),
                setOnInsert(DailyCounterDocument::count, 0),
                inc(DailyCounterDocument::count, 1)
            ),
            options = FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER)
        )

        return result != null
    }
}
