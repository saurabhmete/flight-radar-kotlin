package org.ssm.flightradar.config

import org.ssm.flightradar.datasource.AwsParameterStore

data class AppConfig(
    val port: Int,
    val mongoUri: String,
    val mongoDb: String,
    val openskyClientId: String,
    val openskyClientSecret: String,

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

                centerLat = (System.getenv("CENTER_LAT") ?: "51.5136").toDouble(),
                centerLon = (System.getenv("CENTER_LON") ?: "7.4653").toDouble(),
                bboxDeltaDeg = (System.getenv("BBOX_DELTA_DEG") ?: "1.0").toDouble()
            )
        }
    }
}
