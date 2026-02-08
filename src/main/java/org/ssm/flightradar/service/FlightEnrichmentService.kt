package org.ssm.flightradar.service

import org.slf4j.LoggerFactory
import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.domain.NearbyFlight
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.service.enrichment.AircraftImageResolver
import org.ssm.flightradar.service.enrichment.RouteEnricher
import org.ssm.flightradar.service.timeout.TimeoutRunner
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.ssm.flightradar.domain.AircraftImageType

/**
 * Best-effort enrichment executed during request handling.
 *
 * Principles:
 * - Never throw: if enrichment fails, we return the base flight.
 * - Always time-box external calls.
 * - Cache both successes and "not found" to avoid hammering providers.
 */
class FlightEnrichmentService(
    openSky: OpenSkyDataSource,
    private val cache: FlightCacheRepository
) {

    private val log = LoggerFactory.getLogger(FlightEnrichmentService::class.java)

    private val httpClient = HttpClient(CIO)
    private val routeEnricher = RouteEnricher(openSky)
    private val imageResolver = AircraftImageResolver(httpClient)

    private val routeTimeoutMs = 500L

    // How far back we look for callsign history. Keep small to reduce payloads.
    private val historyLookbackSeconds = 6 * 60 * 60L

    // If we couldn't resolve a route, don't retry for 24h.
    private val routeNegativeCacheSeconds = 24 * 60 * 60L

    suspend fun enrich(base: NearbyFlight, nowEpoch: Long): NearbyFlight {
        // Ensure we have a cache doc so we can store enrichment decisions.
        cache.upsertObservation(base.callsign, base.icao24, nowEpoch)

        val cached = cache.getCachedFlight(base.callsign)

        var enriched = base

        // -------------------------
        // Route enrichment
        // -------------------------
        val hasRoute = !cached?.departure.isNullOrBlank() && !cached.arrival.isNullOrBlank()

        if (hasRoute) {
            enriched = enriched.copy(
                departure = cached.departure,
                departureName = cached.departureName,
                arrival = cached.arrival,
                arrivalName = cached.arrivalName
            )
        } else {
            val notFoundUntil = cached?.routeNotFoundUntilEpoch
            val canTry = (notFoundUntil == null) || (nowEpoch >= notFoundUntil)

            if (canTry) {
                val begin = nowEpoch - historyLookbackSeconds
                val route = TimeoutRunner.run(routeTimeoutMs) {
                    routeEnricher.resolveRouteBestEffort(
                        callsign = base.callsign,
                        beginEpoch = begin,
                        endEpoch = nowEpoch
                    )
                }

                if (route != null) {
                    cache.updateRoute(
                        callsign = base.callsign,
                        departure = route.departure,
                        arrival = route.arrival,
                        routeCheckedAtEpoch = nowEpoch,
                        routeNotFoundUntilEpoch = null
                    )

                    enriched = enriched.copy(
                        departure = route.departure,
                        arrival = route.arrival
                    )
                } else {
                    cache.updateRoute(
                        callsign = base.callsign,
                        departure = null,
                        arrival = null,
                        routeCheckedAtEpoch = nowEpoch,
                        routeNotFoundUntilEpoch = nowEpoch + routeNegativeCacheSeconds
                    )
                }
            }
        }

// -------------------------
// Aircraft image enrichment
// -------------------------
        val cachedImageUrl = cached?.aircraftImageUrl
        val cachedImageType = cached?.aircraftImageType

        if (!cachedImageUrl.isNullOrBlank() && cachedImageType != null) {
            val typeEnum = runCatching {
                AircraftImageType.valueOf(cachedImageType)
            }.getOrNull()

            enriched = enriched.copy(
                aircraftImageUrl = cachedImageUrl,
                aircraftImageType = typeEnum
            )
        } else {
            val resolvedUrl = TimeoutRunner.run(300L) {
                imageResolver.resolveByIcao24(base.icao24)
            }

            val finalUrl = resolvedUrl ?: "/static/aircraft/plane.svg"
            val finalType =
                if (resolvedUrl != null) AircraftImageType.EXACT
                else AircraftImageType.SILHOUETTE

            enriched = enriched.copy(
                aircraftImageUrl = finalUrl,
                aircraftImageType = finalType
            )

            // Cache decision once
            try {
                cache.updateAircraftImage(
                    callsign = base.callsign,
                    aircraftImageUrl = finalUrl,
                    aircraftImageType = finalType
                )
            } catch (t: Throwable) {
                log.debug("Failed to update aircraft image cache for {}", base.callsign, t)
            }
        }



        return enriched
    }
}
