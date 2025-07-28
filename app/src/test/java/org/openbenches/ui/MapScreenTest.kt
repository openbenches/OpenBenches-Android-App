package org.openbenches.ui

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for MapScreen functionality.
 */
class MapScreenTest {
    
    @Test
    fun testMapProviderEnum() {
        // Test that all map providers are available
        assertEquals(1, MapProvider.values().size)
        assertTrue("OSM should be available", MapProvider.values().contains(MapProvider.OSM))
    }
} 