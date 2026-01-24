package org.ssm.flightradar

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.MongoProvider
import org.ssm.flightradar.datasource.OpenSkyClient
import org.ssm.flightradar.service.ArrivalBatchJob

/**
 * CLI entry point for nightly arrival update job.
 *
 * Usage:
 *   ./gradlew run --args="arrival-job"
 *
 * Or in production:
 *   java -jar flight-radar.jar arrival-job
 */
fun main(args: Array<String>) = runBlocking {

    val log = LoggerFactory.getLogger("ArrivalJobMain")

    if (args.isEmpty() || args[0] != "arrival-job") {
        log.info("No arrival job argument supplied, exiting")
        return@runBlocking
    }

    log.info("Starting arrival batch job")

    val config = AppConfig.fromEnv()
    val mongo = MongoProvider(config)
    val openSky = OpenSkyClient(config)

    val job = ArrivalBatchJob(
        mongo = mongo,
        openSky = openSky
    )

    job.runOnce()

    log.info("Arrival batch job finished")
}
