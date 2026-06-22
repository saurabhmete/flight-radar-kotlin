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
import org.ssm.flightradar.service.ImageProxyService
import org.ssm.flightradar.util.AirportLookupService
import io.ktor.http.*
import io.ktor.server.request.*

fun Application.registerRoutes(config: AppConfig) {

    val mongo = MongoProvider(config)
    val openSky = OpenSkyClient(config)
    val airportLookup = AirportLookupService()
    val enrichment = FlightEnrichmentService(config, mongo, airportLookup)
    val service = FlightService(openSky, mongo, config, enrichment)
    val imageProxy = ImageProxyService()

    routing {
        // Simple OLED-friendly dashboard for an old Android phone.
        staticResources("/static", "static")
        get("/") {
            call.respondRedirect("/static/index.html")
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/api/image/proxy") {
            val url = call.request.queryParameters["u"]
            val w = call.request.queryParameters["w"]?.toIntOrNull() ?: 320
            val h = call.request.queryParameters["h"]?.toIntOrNull() ?: 240
            if (url.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "missing u")
                return@get
            }
            val result = imageProxy.fetch(url, w, h)
            if (result == null) {
                call.respond(HttpStatusCode.BadGateway, "image fetch failed")
                return@get
            }
            call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=86400")
            call.respondBytes(result.bytes, ContentType.parse(result.contentType))
        }

        route("/api/flights") {
            get("/nearby") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 3
                val maxDistance = call.request.queryParameters["max_distance_km"]?.toDoubleOrNull() ?: 80.0

                require(limit in 1..20) { "limit must be between 1 and 20" }
                require(maxDistance in 1.0..500.0) { "max_distance_km must be between 1 and 500" }

                val flights = service.nearby(limit)
                call.respond(NearbyFlightsResponseDto(flights.map { it.toDto() }))
            }
        }
    }
}
