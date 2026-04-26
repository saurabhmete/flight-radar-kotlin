package org.ssm.flightradar.service

import kotlinx.coroutines.runBlocking
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.domain.AircraftImageType
import org.ssm.flightradar.domain.FlightState
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.util.AirportLookupService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlightServiceTest {

    // Minimal config: AeroAPI attempts = 0 so no outbound paid calls are made in tests.
    private val config = AppConfig(
        port = 8080,
        mongoUri = "mongodb://localhost:27017",
        mongoDb = "test",
        openskyClientId = "x",
        openskyClientSecret = "y",
        aeroApiKey = "x",
        aeroApiBaseUrl = "https://aeroapi.flightaware.com/aeroapi",
        flightWallCdnBaseUrl = "https://cdn.theflightwall.com",
        maxAeroApiCallsPerDay = 0,
        aeroApiNegativeCacheSeconds = 21600,
        aeroApiMaxAttemptsPerCallsign = 0,
        centerLat = 51.5136,
        centerLon = 7.4653,
        bboxDeltaDeg = 1.0,
        maxDistanceKm = 40.0
    )

    private val noopCache = object : FlightCacheRepository {
        override suspend fun getCachedFlight(callsign: String) = null
        override suspend fun upsertObservation(callsign: String, icao24: String, nowEpoch: Long) = Unit
        override suspend fun updateAircraftImage(callsign: String, aircraftImageUrl: String, aircraftImageType: AircraftImageType) = Unit
        override suspend fun updateEnrichment(
            callsign: String, departureIcao: String?, arrivalIcao: String?,
            operatorIcao: String?, aircraftTypeIcao: String?, operatorName: String?,
            aircraftNameShort: String?, aircraftNameFull: String?,
            aeroApiCheckedAtEpoch: Long?, aeroApiNotFoundUntilEpoch: Long?,
            aeroApiAttemptCountDelta: Int
        ) = Unit
        override suspend fun tryAcquireAeroApiSlot(utcDate: String, maxPerDay: Int) = false
    }

    private fun makeService(states: List<FlightState>): FlightService {
        val openSky = object : OpenSkyDataSource {
            override suspend fun getStatesInBoundingBox(
                lamin: Double, lomin: Double, lamax: Double, lomax: Double
            ) = states
        }
        val enrichment = FlightEnrichmentService(config, noopCache, AirportLookupService())
        return FlightService(openSky, noopCache, config, enrichment)
    }

    // Two flights near Dortmund (HOME_LAT=51.505, HOME_LON=7.466) at cruise altitude.
    // At 10 000 m, effective visibility radius ≈ 89 km, well above MAX_DISTANCE_KM (40 km).
    private val close = FlightState("aa", "CSE1", 51.52, 7.47, 10_000.0, 250.0, 90.0)  // ~2 km
    private val far   = FlightState("bb", "CSE2", 51.65, 7.70, 10_000.0, 250.0, 45.0)  // ~20 km

    @Test
    fun `nearby returns flights sorted by distance ascending`() = runBlocking {
        val result = makeService(listOf(far, close)).nearby(limit = 10)
        assertEquals(2, result.size)
        assertTrue(result[0].distanceKm < result[1].distanceKm, "Expected closest flight first")
    }

    @Test
    fun `nearby respects limit and returns closest`() = runBlocking {
        val result = makeService(listOf(far, close)).nearby(limit = 1)
        assertEquals(1, result.size)
        assertEquals("CSE1", result[0].callsign)
    }

    @Test
    fun `nearby filters out low-altitude flights`() = runBlocking {
        val lowAlt = FlightState("cc", "CSE3", 51.52, 7.47, 100.0, 100.0, 0.0)
        val result = makeService(listOf(lowAlt)).nearby(limit = 10)
        assertEquals(0, result.size)
    }
}
