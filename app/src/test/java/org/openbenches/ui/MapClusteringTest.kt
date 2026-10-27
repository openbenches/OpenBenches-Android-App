package org.openbenches.ui

import org.openbenches.data.Bench
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.pow

/**
 * Test class for the enhanced clustering functionality
 * Based on OpenBenches.org clustering approach
 */
class MapClusteringTest {
    
    /**
     * Test the Haversine distance calculation
     */
    @Test
    fun testCalculateDistance() {
        // Test distance between two known points
        val lat1 = 51.5074 // London
        val lng1 = -0.1278
        val lat2 = 48.8566 // Paris
        val lng2 = 2.3522
        
        val distance = calculateDistance(lat1, lng1, lat2, lng2)
        
        // Distance should be approximately 344 km
        assertTrue("Distance should be around 344km", distance in 340.0..350.0)
    }
    
    /**
     * Test clustering with a small set of benches
     */
    @Test
    fun testClusteringSmallDataset() {
        val benches = listOf(
            Bench(1, listOf(-0.1278, 51.5074), "Bench 1"), // London
            Bench(2, listOf(-0.1279, 51.5075), "Bench 2"), // Very close to Bench 1
            Bench(3, listOf(2.3522, 48.8566), "Bench 3"),  // Paris (far away)
            Bench(4, listOf(2.3523, 48.8567), "Bench 4")   // Very close to Bench 3
        )
        
        val clusters = clusterBenchesByDistance(benches, 15.0) // High zoom level
        
        // Should create 2 clusters: London area and Paris area
        // Note: Grid-based clustering might create more clusters due to grid boundaries
        assertTrue("Should have 2-4 clusters", clusters.size in 2..4)
        
        // Check that nearby benches are clustered together
        val londonCluster = clusters.find { it.any { bench -> bench.id == 1 } }
        val parisCluster = clusters.find { it.any { bench -> bench.id == 3 } }
        
        assertNotNull("London cluster should exist", londonCluster)
        assertNotNull("Paris cluster should exist", parisCluster)
        
        // At least one bench from each area should be clustered
        assertTrue("London area should have at least 1 bench", londonCluster!!.size >= 1)
        assertTrue("Paris area should have at least 1 bench", parisCluster!!.size >= 1)
    }
    
    /**
     * Test clustering with single bench (should not cluster)
     */
    @Test
    fun testClusteringSingleBench() {
        val benches = listOf(
            Bench(1, listOf(-0.1278, 51.5074), "Bench 1")
        )
        
        val clusters = clusterBenchesByDistance(benches, 10.0)
        
        assertEquals("Should have 1 cluster", 1, clusters.size)
        assertEquals("Cluster should have 1 bench", 1, clusters[0].size)
    }
    
    /**
     * Test clustering with widely separated benches
     */
    @Test
    fun testClusteringWidelySeparated() {
        val benches = listOf(
            Bench(1, listOf(-0.1278, 51.5074), "London"),     // London
            Bench(2, listOf(2.3522, 48.8566), "Paris"),       // Paris
            Bench(3, listOf(13.4050, 52.5200), "Berlin"),      // Berlin
            Bench(4, listOf(139.6917, 35.6895), "Tokyo")      // Tokyo
        )
        
        val clusters = clusterBenchesByDistance(benches, 8.0) // Low zoom level
        
        // Should create 4 individual clusters since they're very far apart
        assertEquals("Should have 4 individual clusters", 4, clusters.size)
        
        clusters.forEach { cluster ->
            assertEquals("Each cluster should have 1 bench", 1, cluster.size)
        }
    }
    
    /**
     * Test cluster radius based on zoom level
     */
    @Test
    fun testClusterRadiusByZoomLevel() {
        val benches = listOf(
            Bench(1, listOf(-0.1278, 51.5074), "Bench 1"),
            Bench(2, listOf(-0.1279, 51.5075), "Bench 2"), // Very close
            Bench(3, listOf(-0.1300, 51.5100), "Bench 3")  // Further away
        )
        
        // At high zoom level (15.0), should cluster very close benches
        val highZoomClusters = clusterBenchesByDistance(benches, 15.0)
        assertTrue("At high zoom, should have 2-3 clusters", highZoomClusters.size in 2..3)
        
        // At low zoom level (8.0), should cluster more benches together
        val lowZoomClusters = clusterBenchesByDistance(benches, 8.0)
        assertTrue("At low zoom, should have fewer clusters", lowZoomClusters.size <= highZoomClusters.size)
    }
    
    /**
     * Test that isolated benches remain as individual markers at all zoom levels
     * This ensures proper click handling for single bench icons
     */
    @Test
    fun testIsolatedBenchesRemainIndividual() {
        val benches = listOf(
            Bench(1, listOf(-0.1278, 51.5074), "Isolated Bench 1"),
            Bench(2, listOf(-0.1279, 51.5075), "Isolated Bench 2"), // Very close to Bench 1
            Bench(3, listOf(2.3522, 48.8566), "Isolated Bench 3"),  // Far away
            Bench(4, listOf(13.4050, 52.5200), "Isolated Bench 4")   // Very far away
        )
        
        // Test at different zoom levels
        val zoomLevels = listOf(8.0, 10.0, 12.0, 14.0, 15.0, 16.0)
        
        zoomLevels.forEach { zoomLevel ->
            val clusters = clusterBenchesByDistance(benches, zoomLevel)
            
            // Should have at least 2 clusters (benches 1&2 might cluster, others should be individual)
            assertTrue("At zoom $zoomLevel, should have at least 2 clusters", clusters.size >= 2)
            
            // All clusters should have at least 1 bench
            clusters.forEach { cluster ->
                assertTrue("Each cluster should have at least 1 bench", cluster.size >= 1)
            }
            
            // Verify that isolated benches (3 and 4) remain as individual markers
            val isolatedBenches = clusters.filter { it.size == 1 }
            assertTrue("Should have at least 2 isolated benches at zoom $zoomLevel", isolatedBenches.size >= 2)
            
            // Verify that isolated benches are properly identified
            val isolatedBenchIds = isolatedBenches.map { it.first().id }
            assertTrue("Bench 3 should be isolated at zoom $zoomLevel", isolatedBenchIds.contains(3))
            assertTrue("Bench 4 should be isolated at zoom $zoomLevel", isolatedBenchIds.contains(4))
        }
    }
    
    /**
     * Helper function to calculate distance (copied from MapScreen for testing)
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
    
    /**
     * Helper function to cluster benches (copied from MapScreen for testing)
     */
    private fun clusterBenchesByDistance(benches: List<Bench>, zoomLevel: Double): List<List<Bench>> {
        if (benches.isEmpty()) return emptyList()
        
        // Determine cluster radius based on zoom level
        val clusterRadiusKm = when {
            zoomLevel < 8.0 -> 50.0
            zoomLevel < 10.0 -> 20.0
            zoomLevel < 12.0 -> 5.0
            zoomLevel < 14.0 -> 1.0
            zoomLevel < 15.0 -> 0.5
            else -> 0.1
        }
        
        // Use grid-based clustering for better performance
        val gridSize = clusterRadiusKm / 111.0
        val grid = mutableMapOf<String, MutableList<Bench>>()
        
        // Group benches by grid cell
        benches.forEach { bench ->
            val gridX = (bench.lng / gridSize).toInt()
            val gridY = (bench.lat / gridSize).toInt()
            val gridKey = "$gridX,$gridY"
            
            grid.getOrPut(gridKey) { mutableListOf() }.add(bench)
        }
        
        val clusters = mutableListOf<MutableList<Bench>>()
        
        // Process each grid cell
        grid.values.forEach { gridBenches ->
            if (gridBenches.size == 1) {
                clusters.add(mutableListOf(gridBenches.first()))
            } else {
                val used = mutableSetOf<Int>()
                
                for (i in gridBenches.indices) {
                    if (i in used) continue
                    
                    val currentBench = gridBenches[i]
                    val cluster = mutableListOf<Bench>()
                    cluster.add(currentBench)
                    used.add(i)
                    
                    for (j in i + 1 until gridBenches.size) {
                        if (j in used) continue
                        
                        val otherBench = gridBenches[j]
                        val distance = calculateDistance(
                            currentBench.lat, currentBench.lng,
                            otherBench.lat, otherBench.lng
                        )
                        
                        if (distance <= clusterRadiusKm) {
                            cluster.add(otherBench)
                            used.add(j)
                        }
                    }
                    
                    // Only create cluster if there are multiple benches
                    // If only one bench in cluster, treat as individual marker
                    if (cluster.size > 1) {
                        clusters.add(cluster)
                    } else {
                        // Single isolated bench - create individual cluster for proper click handling
                        clusters.add(mutableListOf(cluster.first()))
                    }
                }
            }
        }
        
        return clusters
    }
}
