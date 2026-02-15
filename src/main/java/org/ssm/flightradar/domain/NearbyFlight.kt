package org.ssm.flightradar.domain

data class NearbyFlight(
    val icao24: String,
    val callsign: String,

    val altitude: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val velocity: Double? = null,

    val distanceKm: Double,

    val departure: String? = null,
    val departureName: String? = null,

    val arrival: String? = null,
    val arrivalName: String? = null,

    // Operator / aircraft metadata (best-effort)
    val operatorIcao: String? = null,
    val operatorName: String? = null,

    val aircraftTypeIcao: String? = null,
    val aircraftNameShort: String? = null,
    val aircraftNameFull: String? = null,

    /**
     * URL that the client can load directly (either a local static asset, or an external URL).
     */
    val aircraftImageUrl: String? = null,

    /**
     * Indicates whether [aircraftImageUrl] points to an exact aircraft photo or a generic silhouette.
     */
    val aircraftImageType: AircraftImageType? = null
)

enum class AircraftImageType {
    EXACT,
    SILHOUETTE
}
