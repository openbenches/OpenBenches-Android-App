package org.openbenches.ui

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for MapScreen functionality.
 */
class MapScreenTest {
    
    @Test
    fun testMapProviderEnum() {
        assertEquals(2, MapProvider.values().size)
        assertTrue("OSM should be available", MapProvider.values().contains(MapProvider.OSM))
        assertTrue(
            "Esri satellite should be available",
            MapProvider.values().contains(MapProvider.ESRI_SATELLITE),
        )
    }
} 