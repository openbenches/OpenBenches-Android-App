package org.openbenches.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openbenches.data.Bench
import org.osmdroid.util.BoundingBox

class ViewportBenchClustererTest {

    @Test
    fun filterInViewport_returnsOnlyBenchesInsideBox() {
        val benches = listOf(
            Bench(1, listOf(-0.1, 51.5), "A"),
            Bench(2, listOf(2.35, 48.85), "B"),
        )
        val bbox = BoundingBox(52.0, 0.5, 51.0, -0.5)
        val visible = ViewportBenchClusterer.filterInViewport(benches, bbox)
        assertEquals(1, visible.size)
        assertEquals(1, visible.first().id)
    }

    @Test
    fun filterInViewport_emptyWhenNoneInView() {
        val benches = listOf(Bench(1, listOf(139.0, 35.0), "Tokyo"))
        val bbox = BoundingBox(52.0, 0.5, 51.0, -0.5)
        assertTrue(ViewportBenchClusterer.filterInViewport(benches, bbox).isEmpty())
    }

    @Test
    fun quantizeZoomForClustering_usesWholeLevelsWhenFarOut() {
        assertEquals(4.0, ViewportBenchClusterer.quantizeZoomForClustering(4.7), 0.0)
        assertEquals(7.0, ViewportBenchClusterer.quantizeZoomForClustering(7.9), 0.0)
    }

    @Test
    fun quantizeZoomForClustering_usesHalfLevelsWhenCloserIn() {
        assertEquals(10.0, ViewportBenchClusterer.quantizeZoomForClustering(10.4), 0.0)
        assertEquals(10.5, ViewportBenchClusterer.quantizeZoomForClustering(10.6), 0.0)
    }

    @Test
    fun filterInViewport_keepsAllBenchesOnWorldScaleView() {
        val benches = listOf(
            Bench(1, listOf(-0.1, 51.5), "London"),
            Bench(2, listOf(151.2, -33.8), "Sydney"),
            Bench(3, listOf(-122.4, 37.7), "SF"),
        )
        val worldBbox = BoundingBox(85.0, 180.0, -85.0, -180.0)
        assertEquals(3, ViewportBenchClusterer.filterInViewport(benches, worldBbox).size)
    }
}
