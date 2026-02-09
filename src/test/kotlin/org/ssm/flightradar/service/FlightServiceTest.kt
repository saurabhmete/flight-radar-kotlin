package org.ssm.flightradar.service

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.domain.FlightState
import org.ssm.flightradar.persistence.FlightCacheDocument
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.service.enrichment.AircraftImageResolver
import org.ssm.flightradar.service.enrichment.RouteEnricher
import kotlin.test.Test
import kotlin.test.assertEquals

class FlightServiceTest {

    private class FakeOpenSky(private val states: List<FlightState>) : OpenSkyDataSource {
        override suspend fun getStatesInBoundingBox(
            lamin: Double,
            lomin: Double,
            lamax: Double,
            lomax: Double
        ): List<FlightState> = states

        override suspend fun getFlightHistoryByCallsign(
            callsign: String,
            beginEpoch: Long,
            endEpoch: Long
        ): List<JsonObject> = emptyList()
    }

    private class FakeRouteEnricher : RouteEnricher(
        openSky = object : OpenSkyDataSource {
            override suspend fun getFlightHistoryByCallsign(
                callsign: String,
                beginEpoch: Long,
                endEpoch: Long
            ) = emptyList()
        }
    )

    private class FakeAircraftImageResolver : AircraftImageResolver {
        override suspend fun resolve(
            aircraftType: String?,
            registration: String?
        ) = null
    }

    private class FakeCache(private val byCallsign: Map<String, FlightCacheDocument>) : FlightCacheRepository {
        override suspend fun getCachedFlight(callsign: String): FlightCacheDocument? = byCallsign[callsign]

        override suspend fun findFlightsNeedingArrivalUpdate(yesterdayEpoch: Long): List<FlightCacheDocument> = emptyList()
        override suspend fun updateArrival(callsign: String, arrival: String, arrivalName: String?) = Unit
        override suspend fun incrementArrivalRetry(callsign: String) = Unit

        override suspend fun upsertObservation(
            icao24: String,
            callsign: String?
        ) {
        }
    }

    @Test
    fun `nearby sorts by distance and respects limit`() = runBlocking {

        val config = AppConfig(
            port = 8080,
            mongoUri = "mongodb://localhost:27017",
            mongoDb = "flight_radar",
            openskyClientId = "x",
            openskyClientSecret = "y",
            centerLat = 51.5136,
            centerLon = 7.4653,
            bboxDeltaDeg = 1.0
        )

        val states = listOf(
            FlightState(icao24 = "a", callsign = "CS2", lat = 51.60, lon = 7.70, altitude = 100.0, velocity = 200.0),
            FlightState(icao24 = "b", callsign = "CS1", lat = 51.52, lon = 7.47, altitude = 100.0, velocity = 200.0)
        )

        val cache = mapOf(
            "CS1" to FlightCacheDocument(
                id = null,
                callsign = "CS1",
                icao24 = "b",
                departure = "EDDF",
                departureName = "Frankfurt",
                arrival = null,
                arrivalName = null,
                firstSeenEpoch = 0L,
                cachedAtEpoch = 0L
            )
        )

        val enrichment = FlightEnrichmentService(
            routeEnricher = FakeRouteEnricher(),
            imageResolver = FakeAircraftImageResolver(),
            clock = clock
        )

        val service = FlightService(
            openSky = fakeOpenSky,
            cache = fakeCache,
            enrichment = enrichment,
            clock = clock
        )

        val result = service.nearby(limit = 1, maxDistanceKm = 500.0)
        assertEquals(1, result.size)
        assertEquals("CS1", result.first().callsign)
        assertEquals("EDDF", result.first().departure)
    }
}
