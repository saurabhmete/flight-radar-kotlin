package org.ssm.flightradar.config

import org.ssm.flightradar.datasource.AwsParameterStore

data class AppConfig(
    val port: Int,
    val mongoUri: String,
    val mongoDb: String,
    val openskyClientId: String,
    val openskyClientSecret: String
) {
    companion object {

        fun fromEnv(): AppConfig {

            // This is set automatically on EC2
            val isAws = System.getenv("AWS_EXECUTION_ENV") != null

            val ssm = if (isAws) AwsParameterStore() else null

            fun secret(envName: String, ssmName: String): String {
                return if (isAws) {
                    ssm!!.getSecureParameter(ssmName)
                } else {
                    System.getenv(envName)
                        ?: error("Missing env var: $envName")
                }
            }

            return AppConfig(
                port = (System.getenv("PORT") ?: "8080").toInt(),
                mongoUri = System.getenv("MONGO_URI") ?: "mongodb://localhost:27017",
                mongoDb = System.getenv("MONGO_DB") ?: "flight_radar",

                openskyClientId = secret(
                    envName = "OPENSKY_CLIENT_ID",
                    ssmName = "opensky_client_id"
                ),
                openskyClientSecret = secret(
                    envName = "OPENSKY_CLIENT_SECRET",
                    ssmName = "opensky_client_secret"
                )
            )
        }
    }
}
