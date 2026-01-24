package org.ssm.flightradar

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.slf4j.event.Level
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

        registerRoutes(config)
    }.start(wait = true)
}
