package org.ssm.flightradar.service.enrichment

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class AircraftImageResolver(
    private val httpClient: HttpClient
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolveByIcao24(icao24: String): String? {
        return try {
            val response: HttpResponse = httpClient.get(
                "https://api.planespotters.net/pub/photos/hex/${icao24.lowercase()}"
            ) {
                header(HttpHeaders.UserAgent, "flight-radar-kotlin/1.0")
            }
            if (response.status != HttpStatusCode.OK) return null

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val photos = root["photos"]?.jsonArray ?: return null
            if (photos.isEmpty()) return null

            val first = photos[0].jsonObject
            first["thumbnail_large"]?.jsonObject?.get("src")?.jsonPrimitive?.contentOrNull
                ?: first["thumbnail"]?.jsonObject?.get("src")?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }
}
