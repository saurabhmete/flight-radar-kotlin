package org.ssm.flightradar.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.slf4j.LoggerFactory
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.AeroApiClient
import org.ssm.flightradar.datasource.FlightWallCdnClient
import org.ssm.flightradar.domain.AircraftImageType
import org.ssm.flightradar.domain.NearbyFlight
import org.ssm.flightradar.persistence.FlightCacheDocument
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.service.enrichment.AircraftImageResolver
import org.ssm.flightradar.service.timeout.TimeoutRunner
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Best-effort enrichment executed during request handling.
 *
 * Principles (cost-aware):
 * - Paid calls are capped per-day (Mongo counter).
 * - Cache both successes and failures (negative cache).
 * - Prefer public CDN for labels where possible.
 */
class FlightEnrichmentService(
    private val config: AppConfig,
    private val cache: FlightCacheRepository
) {
    private val log = LoggerFactory.getLogger(FlightEnrichmentService::class.java)

    private val httpClient = HttpClient(CIO)

    private val aeroApi = AeroApiClient(config)
    private val cdn = FlightWallCdnClient(config)
    private val imageResolver = AircraftImageResolver(httpClient)

    private val aeroTimeoutMs = 900L
    private val utcDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

    suspend fun enrich(base: NearbyFlight, nowEpoch: Long): NearbyFlight {
        // Canonical callsign (OpenSky often pads/varies; always store + lookup canonical)
        val callsign = base.callsign.trim().uppercase()

        // Use canonical callsign everywhere from this point
        var flight = base.copy(callsign = callsign)

        // Ensure doc exists
        cache.upsertObservation(callsign, flight.icao24, nowEpoch)

        val cached = cache.getCachedFlight(callsign)

        // 1) Apply cached enrichment if present
        flight = applyCache(flight, cached)

        // 2) Operator name (cheap) via CDN using callsign prefix
        flight = maybeEnrichOperatorNameFromCdn(flight)

        // 3) Route + aircraft type (paid) via AeroAPI (budget + negative cache)
        flight = maybeEnrichFromAeroApi(flight, cached, nowEpoch)

        // 4) Aircraft image (cheap)
        flight = maybeEnrichAircraftImage(flight)

        return flight
    }

    private fun applyCache(flight: NearbyFlight, cached: FlightCacheDocument?): NearbyFlight {
        if (cached == null) return flight

        var out = flight.copy(
            departure = cached.departureIcao,
            arrival = cached.arrivalIcao,
            operatorIcao = cached.operatorIcao,
            operatorName = cached.operatorName,
            aircraftTypeIcao = cached.aircraftTypeIcao,
            aircraftNameShort = cached.aircraftNameShort,
            aircraftNameFull = cached.aircraftNameFull
        )

        val cachedImageUrl = cached.aircraftImageUrl
        val cachedImageType = cached.aircraftImageType

        if (!cachedImageUrl.isNullOrBlank() && !cachedImageType.isNullOrBlank()) {
            val typeEnum = runCatching { AircraftImageType.valueOf(cachedImageType) }.getOrNull()
            if (typeEnum != null) {
                out = out.copy(
                    aircraftImageUrl = cachedImageUrl,
                    aircraftImageType = typeEnum
                )
            }
        }

        return out
    }

    private suspend fun maybeEnrichOperatorNameFromCdn(flight: NearbyFlight): NearbyFlight {
        if (!flight.operatorName.isNullOrBlank()) return flight

        val candidate = deriveOperatorIcaoCandidate(flight.callsign) ?: return flight
        val operatorIcao = flight.operatorIcao ?: candidate

        val name = TimeoutRunner.run(250L) {
            cdn.lookupAirlineNameFull(operatorIcao)
        }

        if (name.isNullOrBlank()) {
            // Still store operator_icao for UI even if name missing
            runCatching {
                cache.updateEnrichment(
                    callsign = flight.callsign,
                    departureIcao = null,
                    arrivalIcao = null,
                    operatorIcao = operatorIcao,
                    aircraftTypeIcao = null,
                    operatorName = null,
                    aircraftNameShort = null,
                    aircraftNameFull = null,
                    aeroApiCheckedAtEpoch = null,
                    aeroApiNotFoundUntilEpoch = null,
                    aeroApiAttemptCountDelta = 0
                )
            }
            return flight.copy(operatorIcao = operatorIcao)
        }

        runCatching {
            cache.updateEnrichment(
                callsign = flight.callsign,
                departureIcao = null,
                arrivalIcao = null,
                operatorIcao = operatorIcao,
                aircraftTypeIcao = null,
                operatorName = name,
                aircraftNameShort = null,
                aircraftNameFull = null,
                aeroApiCheckedAtEpoch = null,
                aeroApiNotFoundUntilEpoch = null,
                aeroApiAttemptCountDelta = 0
            )
        }

        return flight.copy(operatorIcao = operatorIcao, operatorName = name)
    }

    private suspend fun maybeEnrichFromAeroApi(
        flight: NearbyFlight,
        cached: FlightCacheDocument?,
        nowEpoch: Long
    ): NearbyFlight {
        val alreadyHasRoute = !flight.departure.isNullOrBlank() && !flight.arrival.isNullOrBlank()
        if (alreadyHasRoute) return flight

        val attempts = cached?.aeroApiAttemptCount ?: 0
        if (attempts >= config.aeroApiMaxAttemptsPerCallsign) return flight

        val notFoundUntil = cached?.aeroApiNotFoundUntilEpoch
        val canTry = (notFoundUntil == null) || (nowEpoch >= notFoundUntil)
        if (!canTry) return flight

        val utcDate = utcDateFormatter.format(Instant.ofEpochSecond(nowEpoch))

        val hasBudget = runCatching {
            cache.tryAcquireAeroApiSlot(utcDate, config.maxAeroApiCallsPerDay)
        }.getOrElse {
            log.warn("AeroAPI slot acquisition failed (treat as no-budget) date={}", utcDate, it)
            false
        }

        if (!hasBudget) return flight

        val info = TimeoutRunner.run(aeroTimeoutMs) {
            // FlightAware expects IDENT; you’re using callsign (that’s okay as best-effort)
            aeroApi.fetchFlightInfo(flight.callsign)
        }

        if (info == null) {
            // Negative cache
            runCatching {
                cache.updateEnrichment(
                    callsign = flight.callsign,
                    departureIcao = null,
                    arrivalIcao = null,
                    operatorIcao = null,
                    aircraftTypeIcao = null,
                    operatorName = null,
                    aircraftNameShort = null,
                    aircraftNameFull = null,
                    aeroApiCheckedAtEpoch = nowEpoch,
                    aeroApiNotFoundUntilEpoch = nowEpoch + config.aeroApiNegativeCacheSeconds,
                    aeroApiAttemptCountDelta = 1
                )
            }
            return flight
        }

        // CDN label lookups (cheap)
        val operatorName = info.operatorIcao?.let { code ->
            TimeoutRunner.run(250L) { cdn.lookupAirlineNameFull(code) }
        }

        val (acShort, acFull) = info.aircraftTypeIcao?.let { t ->
            TimeoutRunner.run(250L) { cdn.lookupAircraftNames(t) }
        } ?: (null to null)

        runCatching {
            cache.updateEnrichment(
                callsign = flight.callsign,
                departureIcao = info.originIcao,
                arrivalIcao = info.destinationIcao,
                operatorIcao = info.operatorIcao,
                aircraftTypeIcao = info.aircraftTypeIcao,
                operatorName = operatorName,
                aircraftNameShort = acShort,
                aircraftNameFull = acFull,
                aeroApiCheckedAtEpoch = nowEpoch,
                aeroApiNotFoundUntilEpoch = null,
                aeroApiAttemptCountDelta = 1
            )
        }

        return flight.copy(
            departure = info.originIcao ?: flight.departure,
            arrival = info.destinationIcao ?: flight.arrival,
            operatorIcao = info.operatorIcao ?: flight.operatorIcao,
            operatorName = operatorName ?: flight.operatorName,
            aircraftTypeIcao = info.aircraftTypeIcao ?: flight.aircraftTypeIcao,
            aircraftNameShort = acShort ?: flight.aircraftNameShort,
            aircraftNameFull = acFull ?: flight.aircraftNameFull
        )
    }

    private suspend fun maybeEnrichAircraftImage(flight: NearbyFlight): NearbyFlight {
        if (!flight.aircraftImageUrl.isNullOrBlank() && flight.aircraftImageType != null) return flight

        val resolvedUrl = TimeoutRunner.run(300L) {
            imageResolver.resolveByIcao24(flight.icao24)
        }

        val finalUrl = resolvedUrl ?: "/static/aircraft/plane.svg"
        val finalType = if (resolvedUrl != null) AircraftImageType.EXACT else AircraftImageType.SILHOUETTE

        runCatching {
            cache.updateAircraftImage(
                callsign = flight.callsign,
                aircraftImageUrl = finalUrl,
                aircraftImageType = finalType
            )
        }.onFailure {
            log.debug("Failed to update aircraft image cache for {}", flight.callsign, it)
        }

        return flight.copy(
            aircraftImageUrl = finalUrl,
            aircraftImageType = finalType
        )
    }

    private fun deriveOperatorIcaoCandidate(callsign: String): String? {
        val cs = callsign.trim().uppercase()
        if (cs.length < 3) return null
        val prefix = cs.take(3)
        return if (prefix.all { it in 'A'..'Z' }) prefix else null
    }
}
