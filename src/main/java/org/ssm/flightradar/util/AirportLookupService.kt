package org.ssm.flightradar.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class AirportLookupService {

    private val icaoToAirport: Map<String, Airport>

    init {
        val stream = javaClass.classLoader
            .getResourceAsStream("airports.json")
            ?: error("airports.json not found in src/main/resources")

        val mapper = jacksonObjectMapper()
        val airports: List<Airport> =
            mapper.readValue(stream, object : TypeReference<List<Airport>>() {})

        icaoToAirport = airports.associateBy { it.icao.uppercase() }
    }

    fun findByIcao(icao: String?): Airport? {
        if (icao.isNullOrBlank()) return null
        return icaoToAirport[icao.uppercase()]
    }

    data class Airport(
        val icao: String,
        val iata: String?,
        val name: String
    )
}