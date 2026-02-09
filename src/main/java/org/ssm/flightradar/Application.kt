package org.ssm.flightradar

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.server.response.*
import org.slf4j.event.Level
import org.ssm.flightradar.api.dto.ErrorResponseDto
import org.ssm.flightradar.config.AppConfig
import org.ssm.flightradar.routes.registerRoutes

fun main() {
    val config = AppConfig.fromEnv()

    embeddedServer(Netty, port = config.port) {
        install(CallLogging) {
            level = Level.INFO
        }

        install(ContentNegotiation) {
            json()
        }

        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDto(
                        error = "bad_request",
                        details = cause.message
                    )
                )
            }

            exception<Throwable> { call, cause ->
                call.application.environment.log.error("Unhandled error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponseDto(
                        error = "internal_error",
                        details = "Unexpected server error"
                    )
                )
            }
        }

        registerRoutes(config)

    }.start(wait = true)
}
