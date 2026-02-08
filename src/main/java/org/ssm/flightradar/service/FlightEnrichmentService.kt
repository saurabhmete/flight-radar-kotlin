package org.ssm.flightradar.service

import org.slf4j.LoggerFactory
import org.ssm.flightradar.datasource.OpenSkyDataSource
import org.ssm.flightradar.domain.NearbyFlight
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.service.enrichment.AircraftImageResolver
import org.ssm.flightradar.service.enrichment.RouteEnricher
import org.ssm.flightradar.service.timeout.TimeoutRunner

/**
 * Best-effort enrichment executed during request handling.
 *
 * Principles:
 * - Never throw: if enrichment fails, we return the base flight.
 * - Always time-box external calls.
 * - Cache both successes and "not found" to avoid hammering providers.
 */
class FlightEnrichmentService(
    private val openSky: OpenSkyDataSource,
    private val cache: FlightCacheRepository
) {

    private val log = LoggerFactory.getLogger(FlightEnrichmentService::class.java)

    private val routeEnricher = RouteEnricher(openSky)
    private val imageResolver = AircraftImageResolver()

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
        val hasRoute = !cached?.departure.isNullOrBlank() && !cached?.arrival.isNullOrBlank()

        if (hasRoute) {
            enriched = enriched.copy(
                departure = cached?.departure,
                departureName = cached?.departureName,
                arrival = cached?.arrival,
                arrivalName = cached?.arrivalName
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
        // Aircraft image (local silhouette for now)
        // -------------------------
        val image = imageResolver.resolve()
        enriched = enriched.copy(
            aircraftImageUrl = image.url,
            aircraftImageType = image.type
        )

        // Cache the image fields once (cheap)
        if (cached?.aircraftImageUrl.isNullOrBlank()) {
            try {
                cache.updateAircraftImage(
                    callsign = base.callsign,
                    aircraftImageUrl = image.url,
                    aircraftImageType = image.type.name
                )
            } catch (t: Throwable) {
                // Non-critical
                log.debug("Failed to update aircraft image cache for {}", base.callsign, t)
            }
        }

        return enriched
    }
}
