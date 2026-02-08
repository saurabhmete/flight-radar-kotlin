package org.ssm.flightradar.domain

/**
 * Internal model representing a single live OpenSky state.
 *
 * Note: OpenSky returns callsigns padded with whitespace, so callers should
 * always pass in a trimmed value.
 */
data class FlightState(
    val icao24: String,
    val callsign: String,
    val lat: Double?,
    val lon: Double?,
    val altitude: Double?,
    val velocity: Double?
)
