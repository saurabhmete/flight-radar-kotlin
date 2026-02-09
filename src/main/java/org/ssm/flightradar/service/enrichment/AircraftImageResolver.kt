package org.ssm.flightradar.service.enrichment

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class AircraftImageResolver(
    private val httpClient: HttpClient
) {

    /**
     * Best-effort image resolution using ICAO24 as identifier.
     * Returns a URL if found, otherwise null.
     */
    suspend fun resolveByIcao24(icao24: String): String? {
        val url = wikimediaUrl("$icao24.jpg")
        return if (exists(url)) url else null
    }

    private fun wikimediaUrl(file: String): String =
        "https://commons.wikimedia.org/wiki/Special:FilePath/$file?width=240"

    private suspend fun exists(url: String): Boolean {
        return try {
            val res: HttpResponse = httpClient.head(url)
            res.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        }
    }
}
