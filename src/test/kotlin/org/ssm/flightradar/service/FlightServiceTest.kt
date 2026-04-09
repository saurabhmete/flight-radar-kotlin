package org.ssm.flightradar.service

import kotlinx.coroutines.runBlocking
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.domain.FlightState
import org.ssm.flightradar.domain.NearbyFlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlightServiceTest {

    private val config = AppConfig(
        port = 8080,
        mongoUri = "mongodb://localhost:27017",
        mongoDb = "flight_radar",
        openskyClientId = "x",
        openskyClientSecret = "y",
        aeroApiKey = "x",
        aeroApiBaseUrl = "https://aeroapi.flightaware.com/aeroapi",
        flightWallCdnBaseUrl = "https://cdn.theflightwall.com",
        maxAeroApiCallsPerDay = 30,
        aeroApiNegativeCacheSeconds = 21600L,
        aeroApiMaxAttemptsPerCallsign = 1,
        centerLat = 51.5136,
        centerLon = 7.4653,
        bboxDeltaDeg = 1.0,
        homeLat = 51.505122562296975,
        homeLon = 7.466314232256936
    )

    private class FakeOpenSky(private val states: List<FlightState>) : OpenSkyDataSource {
        override suspend fun getStatesInBoundingBox(
            lamin: Double, lomin: Double, lamax: Double, lomax: Double
        ): List<FlightState> = states
    }

    // No-op enricher: returns the flight unchanged
    private val noopEnricher = FlightEnricher { flight, _ -> flight }

    @Test
    fun `nearby sorts by distance and respects limit`() = runBlocking {
        val states = listOf(
            // Far flight — ~22km, high enough to be visible
            FlightState(icao24 = "a", callsign = "FAR", lat = 51.70, lon = 7.47, altitude = 8000.0, velocity = 200.0),
            // Close flight — ~1km, high enough to be visible
            FlightState(icao24 = "b", callsign = "CLOSE", lat = 51.51, lon = 7.47, altitude = 8000.0, velocity = 200.0)
        )

        val service = FlightService(FakeOpenSky(states), config, noopEnricher)
        val result = service.nearby(limit = 1, maxDistanceKm = 40.0)

        assertEquals(1, result.size)
        assertEquals("CLOSE", result.first().callsign)
    }

    @Test
    fun `nearby excludes flights below minimum altitude`() = runBlocking {
        val states = listOf(
            FlightState(icao24 = "a", callsign = "LOW", lat = 51.51, lon = 7.47, altitude = 100.0, velocity = 100.0),
            FlightState(icao24 = "b", callsign = "HIGH", lat = 51.51, lon = 7.47, altitude = 8000.0, velocity = 200.0)
        )

        val service = FlightService(FakeOpenSky(states), config, noopEnricher)
        val result = service.nearby(limit = 10, maxDistanceKm = 40.0)

        assertEquals(1, result.size)
        assertEquals("HIGH", result.first().callsign)
    }

    @Test
    fun `nearby returns empty when OpenSky returns nothing`() = runBlocking {
        val service = FlightService(FakeOpenSky(emptyList()), config, noopEnricher)
        val result = service.nearby(limit = 3, maxDistanceKm = 40.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nearby excludes flights beyond maxDistanceKm`() = runBlocking {
        val states = listOf(
            // ~22km away, high altitude
            FlightState(icao24 = "a", callsign = "DISTANT", lat = 51.70, lon = 7.47, altitude = 12000.0, velocity = 200.0)
        )

        val service = FlightService(FakeOpenSky(states), config, noopEnricher)

        val withSmallRadius = service.nearby(limit = 3, maxDistanceKm = 5.0)
        assertTrue(withSmallRadius.isEmpty(), "Should be excluded at 5km radius")

        val withLargeRadius = service.nearby(limit = 3, maxDistanceKm = 40.0)
        assertEquals(1, withLargeRadius.size, "Should be included at 40km radius")
    }
}
