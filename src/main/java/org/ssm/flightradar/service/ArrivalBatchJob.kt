package org.ssm.flightradar.service

import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import org.ssm.flightradar.datasource.MongoProvider
import org.ssm.flightradar.datasource.OpenSkyClient
import java.time.LocalDate
import java.time.ZoneOffset

class ArrivalBatchJob(
    private val mongo: MongoProvider,
    private val openSky: OpenSkyClient
) {

    private val log = LoggerFactory.getLogger(ArrivalBatchJob::class.java)

    /**
     * Runs one batch iteration.
     *
     * Rules:
     * - only flights with arrival == null
     * - arrival_retry_count < 3
     * - first_seen <= yesterday
     * - only query yesterday's OpenSky history
     */
    suspend fun runOnce() {

        // OpenSky gives reliable arrival data only for completed flights (yesterday)
        val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        val beginEpoch = yesterday.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val endEpoch = yesterday.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC)

        val candidates = mongo.findFlightsNeedingArrivalUpdate(beginEpoch)

        log.info("Arrival batch job: ${candidates.size} flights to process")

        for (flight in candidates) {
            try {
                log.info("Processing callsign=${flight.callsign}")

                val history = openSky.getFlightHistoryByCallsign(
                    callsign = flight.callsign,
                    beginEpoch = beginEpoch,
                    endEpoch = endEpoch
                )

                if (history.isEmpty()) {
                    log.info("No OpenSky history for ${flight.callsign}")
                    mongo.incrementArrivalRetry(flight.callsign)
                    continue
                }

                // last entry = landing phase
                val last = history.last()

                /*
                  OpenSky flight history fields we care about:
                  - baro_altitude (Double?)
                  - estArrivalAirport (String?)
                */
                val altitude =
                    last["baro_altitude"]?.jsonPrimitive?.doubleOrNull

                val arrivalIcao =
                    last["estArrivalAirport"]?.jsonPrimitive?.contentOrNull

                if (
                    altitude != null &&
                    altitude < 50 &&
                    !arrivalIcao.isNullOrBlank()
                ) {
                    mongo.updateArrival(
                        callsign = flight.callsign,
                        arrival = arrivalIcao,
                        arrivalName = null // optional: airport name enrichment later
                    )
                    log.info("Arrival resolved: ${flight.callsign} → $arrivalIcao")
                } else {
                    log.info("Arrival not resolved for ${flight.callsign}, retrying")
                    mongo.incrementArrivalRetry(flight.callsign)
                }

                // Respect OpenSky rate limits
                delay(1200)

            } catch (ex: Exception) {
                log.error(
                    "Error processing callsign=${flight.callsign}, incrementing retry",
                    ex
                )
                mongo.incrementArrivalRetry(flight.callsign)
            }
        }
    }
}
