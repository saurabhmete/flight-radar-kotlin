package org.ssm.flightradar.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.slf4j.LoggerFactory
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.AeroApiClient
import org.ssm.flightradar.datasource.FlightWallCdnClient
import org.ssm.flightradar.domain.AircraftImageType
import org.ssm.flightradar.domain.NearbyFlight
import org.ssm.flightradar.persistence.FlightCacheRepository
import org.ssm.flightradar.service.enrichment.AircraftImageResolver
import org.ssm.flightradar.service.timeout.TimeoutRunner
import org.ssm.flightradar.util.AirportLookupService
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Best-effort enrichment executed during request handling.
 *
 * Principles (cost-aware):
 * - No batch jobs.
 * - Paid calls are capped per-day (Mongo counter).
 * - Cache both successes and failures (negative cache).
 * - Prefer public CDN for labels where possible.
 */
class FlightEnrichmentService(
    private val config: AppConfig,
    private val cache: FlightCacheRepository,
    private val airportLookup: AirportLookupService
) {

    private val log = LoggerFactory.getLogger(FlightEnrichmentService::class.java)

    private val httpClient = HttpClient(CIO)

    private val aeroApi = AeroApiClient(config)
    private val cdn = FlightWallCdnClient(config)
    private val imageResolver = AircraftImageResolver(httpClient)

    private val aeroTimeoutMs = 900L
    private val utcDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

    suspend fun enrich(base: NearbyFlight, nowEpoch: Long): NearbyFlight {
        // Always normalize callsign: OpenSky may contain padded spaces etc.
        val cleanCallsign = base.callsign.trim().uppercase()

        // Ensure doc exists
        cache.upsertObservation(cleanCallsign, base.icao24, nowEpoch)

        // IMPORTANT: use clean callsign consistently (otherwise cache miss)
        val cached = cache.getCachedFlight(cleanCallsign)

        var enriched = base.copy(callsign = cleanCallsign)

        // 1) Apply cached route & metadata if present
        if (cached != null) {
            enriched = enriched.copy(
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

            if (!cachedImageUrl.isNullOrBlank() && cachedImageType != null) {
                val typeEnum = runCatching { AircraftImageType.valueOf(cachedImageType) }.getOrNull()
                enriched = enriched.copy(
                    aircraftImageUrl = cachedImageUrl,
                    aircraftImageType = typeEnum
                )
            }
        }

        // 2) If operator name missing, try CDN using callsign prefix (cheap)
        enriched = maybeEnrichOperatorNameFromCdn(enriched, cached, cleanCallsign)

        // 3) If route missing, try AeroAPI (paid) within budget and negative-cache
        enriched = maybeEnrichFromAeroApi(enriched, cached, nowEpoch, cleanCallsign)

        // 3.5) Airport IATA + name lookup (local JSON, free)
        enriched = applyAirportLookup(enriched)

        // 4) Aircraft image (cheap)
        enriched = maybeEnrichAircraftImage(enriched, cleanCallsign)

        return enriched
    }

    private fun applyAirportLookup(flight: NearbyFlight): NearbyFlight {
        var out = flight

        val dep = airportLookup.findByIcao(flight.departure)
        if (dep != null) {
            out = out.copy(
                departureName = dep.name,
                departureIata = dep.iata  // can be null -> OK, frontend will fallback to ICAO
            )
        }

        val arr = airportLookup.findByIcao(flight.arrival)
        if (arr != null) {
            out = out.copy(
                arrivalName = arr.name,
                arrivalIata = arr.iata
            )
        }

        return out
    }

    private suspend fun maybeEnrichOperatorNameFromCdn(
        flight: NearbyFlight,
        cached: org.ssm.flightradar.persistence.FlightCacheDocument?,
        cleanCallsign: String
    ): NearbyFlight {
        if (!flight.operatorName.isNullOrBlank()) return flight

        val candidate = deriveOperatorIcaoCandidate(cleanCallsign) ?: return flight
        val operatorIcao = flight.operatorIcao ?: candidate

        val name = TimeoutRunner.run(250L) {
            cdn.lookupAirlineNameFull(operatorIcao)
        }

        if (name.isNullOrBlank()) return flight.copy(operatorIcao = operatorIcao)

        runCatching {
            cache.updateEnrichment(
                callsign = cleanCallsign,
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
        cached: org.ssm.flightradar.persistence.FlightCacheDocument?,
        nowEpoch: Long,
        cleanCallsign: String
    ): NearbyFlight {
        val alreadyHasRoute = !flight.departure.isNullOrBlank() && !flight.arrival.isNullOrBlank()
        if (alreadyHasRoute) return flight

        // Cooldown gate first: don't retry while we're still in the negative-cache window.
        val notFoundUntil = cached?.aeroApiNotFoundUntilEpoch
        val inCooldown = notFoundUntil != null && nowEpoch < notFoundUntil
        if (inCooldown) {
            log.info("AeroAPI skipped {} — in cooldown until {}", cleanCallsign, notFoundUntil)
            return flight
        }

        // Attempt cap second: once cooldown expires the count is checked, so attempts are
        // consumed across cooldown windows (maxAttempts=1 → one lifetime try, =2 → two, etc.).
        val attempts = cached?.aeroApiAttemptCount ?: 0
        if (attempts >= config.aeroApiMaxAttemptsPerCallsign) {
            log.info("AeroAPI skipped {} — attempt cap reached ({}/{})", cleanCallsign, attempts, config.aeroApiMaxAttemptsPerCallsign)
            return flight
        }

        val utcDate = utcDateFormatter.format(Instant.ofEpochSecond(nowEpoch))

        val hasBudget = runCatching {
            cache.tryAcquireAeroApiSlot(utcDate, config.maxAeroApiCallsPerDay)
        }.getOrElse {
            log.warn("AeroAPI slot acquisition failed (treat as no-budget) date={}", utcDate, it)
            false
        }

        if (!hasBudget) return flight

        val info = TimeoutRunner.run(aeroTimeoutMs) {
            aeroApi.fetchFlightInfo(cleanCallsign)
        }

        if (info == null) {
            // Negative cache
            runCatching {
                cache.updateEnrichment(
                    callsign = cleanCallsign,
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

        val operatorName = info.operatorIcao?.let { code ->
            TimeoutRunner.run(250L) { cdn.lookupAirlineNameFull(code) }
        }

        val (acShort, acFull) = info.aircraftTypeIcao?.let { t ->
            TimeoutRunner.run(250L) { cdn.lookupAircraftNames(t) }
        } ?: (null to null)

        runCatching {
            cache.updateEnrichment(
                callsign = cleanCallsign,
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

    private suspend fun maybeEnrichAircraftImage(
        flight: NearbyFlight,
        cleanCallsign: String
    ): NearbyFlight {
        if (flight.aircraftImageType == AircraftImageType.EXACT) return flight

        val resolvedUrl = TimeoutRunner.run(1500L) {
            imageResolver.resolveByIcao24(flight.icao24)
        }

        val finalUrl = resolvedUrl ?: "/static/aircraft/plane.svg"
        val finalType = if (resolvedUrl != null) AircraftImageType.EXACT else AircraftImageType.SILHOUETTE

        runCatching {
            cache.updateAircraftImage(
                callsign = cleanCallsign,
                aircraftImageUrl = finalUrl,
                aircraftImageType = finalType
            )
        }.onFailure {
            log.debug("Failed to update aircraft image cache for {}", cleanCallsign, it)
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