package org.ssm.flightradar.datasource

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

class AwsParameterStore {

    private val ssm = SsmClient.builder()
        .region(Region.EU_CENTRAL_1)
        .build()

    fun getSecureParameter(name: String): String {
        return ssm.getParameter(
            GetParameterRequest.builder()
                .name(name)
                .withDecryption(true)
                .build()
        ).parameter().value()
    }

    fun getParameter(name: String): String {
        return ssm.getParameter(
            GetParameterRequest.builder()
                .name(name)
                .build()
        ).parameter().value()
    }
}

