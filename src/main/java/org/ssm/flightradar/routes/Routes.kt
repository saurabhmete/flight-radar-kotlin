package org.ssm.flightradar.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.datasource.MongoProvider
import org.ssm.flightradar.datasource.OpenSkyClient
import org.ssm.flightradar.model.NearbyFlightsResponse
import org.ssm.flightradar.service.FlightService

fun Application.registerRoutes(config: AppConfig) {

    val mongo = MongoProvider(config)
    val openSky = OpenSkyClient(config)
    val service = FlightService(openSky, mongo)

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api/flights") {
            get("/nearby") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 3
                val maxDistance = call.request.queryParameters["max_distance_km"]?.toDoubleOrNull() ?: 80.0

                val flights = service.nearby(limit, maxDistance)
                call.respond(NearbyFlightsResponse(flights))
            }
        }
    }
}
