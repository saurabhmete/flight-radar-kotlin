package org.ssm.flightradar.config

import org.ssm.flightradar.datasource.AwsParameterStore

data class AppConfig(
    val port: Int,
    val mongoUri: String,
    val mongoDb: String,
    val openskyClientId: String,
    val openskyClientSecret: String,

    // FlightAware AeroAPI
    val aeroApiKey: String,
    val aeroApiBaseUrl: String,

    // Optional public CDN lookups (AxisNimble / TheFlightWall)
    val flightWallCdnBaseUrl: String,

    // Budget controls
    val maxAeroApiCallsPerDay: Int,
    val aeroApiNegativeCacheSeconds: Long,
    val aeroApiMaxAttemptsPerCallsign: Int,

    /**
     * Center point for "nearby flights" queries.
     * Defaults to Dortmund (easy to override in deployments).
     */
    val centerLat: Double,
    val centerLon: Double,

    /**
     * Bounding box half-size in degrees (rough filter before distance calc).
     */
    val bboxDeltaDeg: Double
) {
    companion object {

        fun fromEnv(): AppConfig {

            val ssm = AwsParameterStore()

            fun secret(
                envName: String,
                ssmName: String,
                default: String? = null
            ): String {
                return System.getenv(envName)
                    ?: runCatching { ssm.getSecureParameter(ssmName) }.getOrNull()
                    ?: default
                    ?: error("Missing config: $envName / $ssmName")
            }

            fun value(
                envName: String,
                ssmName: String,
                default: String
            ): String {
                return System.getenv(envName)
                    ?: runCatching { ssm.getSecureParameter(ssmName) }.getOrNull()
                    ?: default
            }

            return AppConfig(
                port = System.getenv("PORT")?.toInt()
                    ?: 8080,

                mongoUri = secret(
                    envName = "MONGO_URI",
                    ssmName = "/flight-radar/mongo/uri",
                    default = "mongodb://localhost:27017"
                ),

                mongoDb = value(
                    envName = "MONGO_DB",
                    ssmName = "/flight-radar/mongo/db",
                    default = "flight_radar"
                ),

                openskyClientId = secret(
                    envName = "OPENSKY_CLIENT_ID",
                    ssmName = "opensky_client_id"
                ),

                openskyClientSecret = secret(
                    envName = "OPENSKY_CLIENT_SECRET",
                    ssmName = "opensky_client_secret"
                ),

                aeroApiKey = secret(
                    envName = "AEROAPI_KEY",
                    ssmName = "/flight-radar/aeroapi/key"
                ),

                aeroApiBaseUrl = value(
                    envName = "AEROAPI_BASE_URL",
                    ssmName = "/flight-radar/aeroapi/base_url",
                    default = "https://aeroapi.flightaware.com/aeroapi"
                ),

                flightWallCdnBaseUrl = value(
                    envName = "FLIGHTWALL_CDN_BASE_URL",
                    ssmName = "/flight-radar/flightwall/cdn_base_url",
                    default = "https://cdn.theflightwall.com"
                ),

                maxAeroApiCallsPerDay = (System.getenv("AEROAPI_MAX_CALLS_PER_DAY") ?: "30").toInt(),
                aeroApiNegativeCacheSeconds = (System.getenv("AEROAPI_NEGATIVE_CACHE_SECONDS") ?: "21600").toLong(), // 6h
                aeroApiMaxAttemptsPerCallsign = (System.getenv("AEROAPI_MAX_ATTEMPTS_PER_CALLSIGN") ?: "1").toInt(),

                centerLat = (System.getenv("CENTER_LAT") ?: "51.2895").toDouble(),
                centerLon = (System.getenv("CENTER_LON") ?: "6.7668").toDouble(),
                bboxDeltaDeg = (System.getenv("BBOX_DELTA_DEG") ?: "1.0").toDouble()
            )
        }
    }
}
