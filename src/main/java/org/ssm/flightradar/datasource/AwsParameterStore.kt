package org.ssm.flightradar.datasource

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

class AwsParameterStore(region: Region = Region.EU_CENTRAL_1) {

    private val client: SsmClient = SsmClient.builder()
        .region(region)
        .build()

    fun getSecureParameter(name: String): String {
        val request = GetParameterRequest.builder()
            .name(name)
            .withDecryption(true)
            .build()

        return client.getParameter(request)
            .parameter()
            .value()
    }
}
