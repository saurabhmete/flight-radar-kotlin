package org.ssm.flightradar.service.enrichment

import org.ssm.flightradar.domain.AircraftImageType

/**
 * Resolves an aircraft image for UI display.
 *
 * Current behaviour:
 * - Always returns a local silhouette (guaranteed, legal, and OLED-friendly).
 *
 * Future extension point:
 * - When we have reliable registration / type data, we can optionally resolve an exact photo
 *   from open-license sources (e.g. Wikimedia Commons) and cache only the URL.
 */
class AircraftImageResolver(
    private val silhouettePath: String = "/static/aircraft/plane.svg"
) {

    data class Image(val url: String, val type: AircraftImageType)

    fun resolve(): Image = Image(
        url = silhouettePath,
        type = AircraftImageType.SILHOUETTE
    )
}
