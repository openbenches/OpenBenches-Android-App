package org.openbenches.ui

import android.graphics.Point
import org.openbenches.data.Bench
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Viewport-aware clustering for large global bench datasets.
 * Uses geographic cells when zoomed far out (avoids map-projection issues) and
 * screen-pixel cells when closer in.
 */
object ViewportBenchClusterer {

    /** Below this zoom, cluster in lat/lng degrees instead of screen pixels. */
    private const val GEO_CLUSTER_MAX_ZOOM = 5.5

    /**
     * Snap zoom for clustering so cell sizes and marker rebuilds do not change every
     * pinch frame. Whole levels when far out; half levels when closer in.
     */
    fun quantizeZoomForClustering(zoomLevel: Double): Double = when {
        zoomLevel >= 16.0 -> zoomLevel
        zoomLevel < 8.0 -> floor(zoomLevel)
        else -> floor(zoomLevel * 2.0) / 2.0
    }

    /** Minimum map pan (degrees) before clusters are recomputed at a given cluster zoom. */
    fun viewportPanGridStep(clusterZoom: Double): Double = when {
        clusterZoom < 5.0 -> 6.0
        clusterZoom < 7.0 -> 2.5
        clusterZoom < 9.0 -> 1.0
        clusterZoom < 11.0 -> 0.35
        clusterZoom < 13.0 -> 0.12
        clusterZoom < 15.0 -> 0.04
        else -> 0.015
    }

    data class BenchCluster(
        val benches: List<Bench>,
        val centerLat: Double,
        val centerLng: Double,
    )

    data class ClusterResult(
        val clusters: List<BenchCluster>,
        val inViewport: Int,
        val mapped: Int,
    )

    fun filterInViewport(
        benches: List<Bench>,
        bbox: BoundingBox,
        paddingFraction: Double = 0.08,
    ): List<Bench> {
        if (benches.isEmpty()) return emptyList()

        val latSpan = (bbox.latNorth - bbox.latSouth).coerceAtLeast(0.0001)
        var lonSpan = bbox.lonEast - bbox.lonWest
        if (lonSpan < 0) lonSpan += 360.0

        // World / continent scale — strict lon/lat box checks drop most benches at min zoom.
        if (latSpan >= 90.0 && lonSpan >= 250.0) {
            return benches
        }

        val latPad = latSpan * paddingFraction
        val lonPad = lonSpan * paddingFraction
        val north = (bbox.latNorth + latPad).coerceAtMost(90.0)
        val south = (bbox.latSouth - latPad).coerceAtLeast(-90.0)
        val east = bbox.lonEast + lonPad
        val west = bbox.lonWest - lonPad

        return benches.filter { bench ->
            bench.lat in south..north && isLongitudeInRange(bench.lng, west, east)
        }
    }

    private fun isLongitudeInRange(lng: Double, west: Double, east: Double): Boolean {
        return if (west <= east) {
            lng in west..east
        } else {
            // Viewport crosses the antimeridian
            lng >= west || lng <= east
        }
    }

    fun cluster(
        benches: List<Bench>,
        mapView: MapView,
        zoomLevel: Double,
    ): ClusterResult {
        if (benches.isEmpty()) {
            return ClusterResult(emptyList(), inViewport = 0, mapped = 0)
        }
        val clusterZoom = quantizeZoomForClustering(zoomLevel)
        if (clusterZoom >= 16.0) {
            val singles = benches.map { bench ->
                BenchCluster(listOf(bench), bench.lat, bench.lng)
            }
            return ClusterResult(singles, inViewport = benches.size, mapped = benches.size)
        }

        val clusters = if (clusterZoom < GEO_CLUSTER_MAX_ZOOM) {
            clusterGeographic(benches, clusterZoom)
        } else {
            clusterByScreenPixels(benches, mapView, clusterZoom)
        }

        val mapped = clusters.sumOf { it.benches.size }
        return ClusterResult(
            clusters = clusters,
            inViewport = benches.size,
            mapped = mapped,
        )
    }

    private fun clusterGeographic(benches: List<Bench>, zoomLevel: Double): List<BenchCluster> {
        var cellDeg = when {
            zoomLevel < 3.0 -> 12.0
            zoomLevel < 4.0 -> 8.0
            zoomLevel < 5.0 -> 5.0
            else -> 3.0
        }
        val maxClusters = maxClustersForZoom(zoomLevel, benches.size)
        var clusters = bucketIntoGeoCells(benches, cellDeg)
        var attempts = 0
        while (clusters.size > maxClusters && attempts < 8) {
            cellDeg *= 1.28
            clusters = bucketIntoGeoCells(benches, cellDeg)
            attempts++
        }
        if (clusters.size > 1 && clusters.size <= 280) {
            val merged = mergeNearbyGeoClusters(clusters, mergeDegrees = cellDeg * 0.9)
            clusters = acceptMergeUnlessTooAggressive(clusters, merged)
        }
        return clusters
    }

    private fun bucketIntoGeoCells(benches: List<Bench>, cellDeg: Double): List<BenchCluster> {
        val grid = mutableMapOf<Long, MutableList<Bench>>()
        for (bench in benches) {
            val gx = floor((bench.lng + 180.0) / cellDeg).toLong()
            val gy = floor((bench.lat + 90.0) / cellDeg).toLong()
            val key = gx * 73856093L xor (gy * 19349663L)
            grid.getOrPut(key) { mutableListOf() }.add(bench)
        }
        return grid.values.map { cellBenches ->
            val (lat, lng) = geographicCenter(cellBenches)
            BenchCluster(cellBenches, lat, lng)
        }
    }

    private fun mergeNearbyGeoClusters(
        cells: List<BenchCluster>,
        mergeDegrees: Double,
    ): List<BenchCluster> {
        if (cells.size <= 1) return cells
        val n = cells.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val mergeDistSq = mergeDegrees * mergeDegrees
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dLat = cells[i].centerLat - cells[j].centerLat
                val dLng = cells[i].centerLng - cells[j].centerLng
                if (dLat * dLat + dLng * dLng <= mergeDistSq) {
                    union(i, j)
                }
            }
        }

        val grouped = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            grouped.getOrPut(find(i)) { mutableListOf() }.add(i)
        }

        val merged = grouped.values.map { indices ->
            val subCells = indices.map { cells[it] }
            val mergedBenches = subCells.flatMap { it.benches }
            val (lat, lng) = centerFromSubCells(subCells)
            BenchCluster(mergedBenches, lat, lng)
        }

        val inputCount = cells.sumOf { it.benches.size }
        val mergedCount = merged.sumOf { it.benches.size }
        return if (mergedCount == inputCount) merged else cells
    }

    private fun clusterByScreenPixels(
        benches: List<Bench>,
        mapView: MapView,
        zoomLevel: Double,
    ): List<BenchCluster> {
        val projection = mapView.projection
        val mapWidth = mapView.width.coerceAtLeast(1)
        val mapHeight = mapView.height.coerceAtLeast(1)
        val maxClusters = maxClustersForZoom(zoomLevel, benches.size)

        var cellSize = effectiveCellSize(zoomLevel, benches.size, mapWidth, mapHeight)
        var clusters = bucketIntoPixelCells(benches, projection, cellSize)

        var attempts = 0
        while (clusters.size > maxClusters && attempts < 8) {
            cellSize *= 1.28
            clusters = bucketIntoPixelCells(benches, projection, cellSize)
            attempts++
        }

        if (clusters.size > 1 && clusters.size <= 280) {
            val merged = mergeNearbyPixelClusters(
                cells = clusters,
                projection = projection,
                mergeDistancePx = cellSize * 0.9,
            )
            clusters = acceptMergeUnlessTooAggressive(clusters, merged)
        }
        return clusters
    }

    /**
     * Allow nearby-cluster merging only when it does not collapse the viewport to a handful
     * of mega-clusters (common when zoomed out with a large merge radius).
     */
    private fun acceptMergeUnlessTooAggressive(
        original: List<BenchCluster>,
        merged: List<BenchCluster>,
    ): List<BenchCluster> {
        val inputCount = original.sumOf { it.benches.size }
        val mergedCount = merged.sumOf { it.benches.size }
        if (mergedCount != inputCount) return original
        val minClusters = max(4, (original.size * 0.4).toInt())
        return if (merged.size >= minClusters) merged else original
    }

    private fun geographicCenter(benches: List<Bench>): Pair<Double, Double> {
        if (benches.isEmpty()) return 0.0 to 0.0
        return benches.map { it.lat }.average() to benches.map { it.lng }.average()
    }

    private fun centerFromSubCells(subCells: List<BenchCluster>): Pair<Double, Double> {
        if (subCells.isEmpty()) return 0.0 to 0.0
        if (subCells.size == 1) return subCells[0].centerLat to subCells[0].centerLng
        return subCells.map { it.centerLat }.average() to subCells.map { it.centerLng }.average()
    }

    private fun effectiveCellSize(
        zoomLevel: Double,
        benchCount: Int,
        mapWidth: Int,
        mapHeight: Int,
    ): Double {
        val zoomBase = clusterRadiusPx(zoomLevel)
        val iconSpacing = clusterMarkerSizePx(zoomLevel, benchCount).toDouble() * when {
            zoomLevel < 7.0 -> 1.15
            zoomLevel < 10.0 -> 1.08
            zoomLevel < 13.0 -> 1.02
            else -> 0.98
        }
        var size = max(zoomBase, iconSpacing)

        val maxClusters = maxClustersForZoom(zoomLevel, benchCount)
        if (benchCount > maxClusters) {
            val mapDiagonal = sqrt((mapWidth * mapWidth + mapHeight * mapHeight).toDouble())
            val densitySize = mapDiagonal / sqrt(maxClusters.toDouble())
            size = max(size, densitySize)
        }

        when {
            benchCount > 20_000 -> size *= 1.35
            benchCount > 10_000 -> size *= 1.25
            benchCount > 5_000 -> size *= 1.12
        }
        return size.coerceAtLeast(clusterMarkerSizePx(zoomLevel, benchCount) * 0.9)
    }

    private fun maxClustersForZoom(zoomLevel: Double, benchCount: Int): Int = when {
        zoomLevel < 6.0 -> 80
        zoomLevel < 8.0 -> 115
        zoomLevel < 10.0 -> 175
        zoomLevel < 12.0 -> 260
        benchCount > 10_000 -> 320
        else -> 420
    }

    private fun bucketIntoPixelCells(
        benches: List<Bench>,
        projection: org.osmdroid.views.Projection,
        cellSize: Double,
    ): List<BenchCluster> {
        val grid = mutableMapOf<Long, MutableList<Bench>>()
        val point = Point()

        for (bench in benches) {
            projection.toPixels(GeoPoint(bench.lat, bench.lng), point)
            val gx = floor(point.x / cellSize).toLong()
            val gy = floor(point.y / cellSize).toLong()
            val key = gx * 73856093L xor (gy * 19349663L)
            grid.getOrPut(key) { mutableListOf() }.add(bench)
        }

        return grid.values.map { cellBenches ->
            val (lat, lng) = geographicCenter(cellBenches)
            BenchCluster(cellBenches, lat, lng)
        }
    }

    private data class CellPixelCenter(
        val cell: BenchCluster,
        val x: Double,
        val y: Double,
    )

    private fun mergeNearbyPixelClusters(
        cells: List<BenchCluster>,
        projection: org.osmdroid.views.Projection,
        mergeDistancePx: Double,
    ): List<BenchCluster> {
        if (cells.size <= 1) return cells

        val point = Point()
        val pixelCenters = cells.map { cell ->
            projection.toPixels(GeoPoint(cell.centerLat, cell.centerLng), point)
            CellPixelCenter(cell, point.x.toDouble(), point.y.toDouble())
        }

        val n = pixelCenters.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val mergeDistSq = mergeDistancePx * mergeDistancePx
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = pixelCenters[i].x - pixelCenters[j].x
                val dy = pixelCenters[i].y - pixelCenters[j].y
                if (dx * dx + dy * dy <= mergeDistSq) {
                    union(i, j)
                }
            }
        }

        val grouped = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            grouped.getOrPut(find(i)) { mutableListOf() }.add(i)
        }

        val merged = grouped.values.map { indices ->
            val subCells = indices.map { pixelCenters[it].cell }
            val mergedBenches = subCells.flatMap { it.benches }
            val (lat, lng) = centerFromSubCells(subCells)
            BenchCluster(mergedBenches, lat, lng)
        }

        val mergedCount = merged.sumOf { it.benches.size }
        val inputCount = cells.sumOf { it.benches.size }
        return if (mergedCount == inputCount) merged else cells
    }

    private fun clusterRadiusPx(zoomLevel: Double): Double = when {
        zoomLevel < 5.0 -> 115.0
        zoomLevel < 7.0 -> 100.0
        zoomLevel < 9.0 -> 85.0
        zoomLevel < 11.0 -> 72.0
        zoomLevel < 13.0 -> 62.0
        zoomLevel < 15.0 -> 50.0
        else -> 40.0
    }
}
