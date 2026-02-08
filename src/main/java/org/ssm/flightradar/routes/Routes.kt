package org.ssm.flightradar.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.MongoProvider
import org.ssm.flightradar.datasource.OpenSkyClient
import org.ssm.flightradar.api.dto.NearbyFlightsResponseDto
import org.ssm.flightradar.api.mapper.toDto
import org.ssm.flightradar.service.FlightService
import org.ssm.flightradar.service.FlightEnrichmentService

fun Application.registerRoutes(config: AppConfig) {

    val mongo = MongoProvider(config)
    val openSky = OpenSkyClient(config)
    val enrichment = FlightEnrichmentService(openSky, mongo)
    val service = FlightService(openSky, mongo, config, enrichment)

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

                require(limit in 1..20) { "limit must be between 1 and 20" }
                require(maxDistance in 1.0..500.0) { "max_distance_km must be between 1 and 500" }

                val flights = service.nearby(limit, maxDistance)
                call.respond(NearbyFlightsResponseDto(flights.map { it.toDto() }))
            }
        }
    }
}
