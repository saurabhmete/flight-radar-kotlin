package org.ssm.flightradar.util

import kotlin.test.Test
import kotlin.test.assertTrue

class GeoTest {

    @Test
    fun `haversineKm returns near-zero for identical coordinates`() {
        val d = Geo.haversineKm(51.0, 7.0, 51.0, 7.0)
        assertTrue(d < 0.001)
    }

    @Test
    fun `haversineKm is symmetric`() {
        val a = Geo.haversineKm(51.5136, 7.4653, 52.52, 13.4050)
        val b = Geo.haversineKm(52.52, 13.4050, 51.5136, 7.4653)
        assertTrue(kotlin.math.abs(a - b) < 1e-9)
    }
}
