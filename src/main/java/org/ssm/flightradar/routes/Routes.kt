package org.ssm.flightradar.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import org.slf4j.LoggerFactory
import org.ssm.flightradar.api.dto.ErrorResponseDto
import org.ssm.flightradar.api.dto.NearbyFlightsResponseDto
import org.ssm.flightradar.api.mapper.toDto
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.MongoProvider
import org.ssm.flightradar.datasource.OpenSkyClient
import org.ssm.flightradar.service.FlightService
import org.ssm.flightradar.service.FlightEnrichmentService
import org.ssm.flightradar.util.AirportLookupService

fun Application.registerRoutes(config: AppConfig) {

    val log = LoggerFactory.getLogger("Routes")

    val mongo = MongoProvider(config)
    val openSky = OpenSkyClient(config)
    val airportLookup = AirportLookupService()
    val enrichment = FlightEnrichmentService(config, mongo, airportLookup)
    val service = FlightService(openSky, config, enrichment)

    routing {
        // Simple OLED-friendly dashboard for an old Android phone.
        staticResources("/static", "static")
        get("/") {
            call.respondRedirect("/static/index.html")
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api/flights") {
            get("/nearby") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 3
                val maxDistance = call.request.queryParameters["max_distance_km"]?.toDoubleOrNull() ?: 80.0

                if (limit !in 1..20) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponseDto("Invalid parameter", "limit must be between 1 and 20")
                    )
                    return@get
                }
                if (maxDistance !in 1.0..500.0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponseDto("Invalid parameter", "max_distance_km must be between 1 and 500")
                    )
                    return@get
                }

                try {
                    val flights = service.nearby(limit, maxDistance)
                    call.respond(NearbyFlightsResponseDto(flights.map { it.toDto() }))
                } catch (e: Exception) {
                    log.error("Failed to fetch nearby flights: {}", e.message)
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponseDto("Service temporarily unavailable", e.message)
                    )
                }
            }
        }
    }
}
