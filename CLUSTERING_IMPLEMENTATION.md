# Enhanced Map Clustering Implementation

## Overview

This implementation provides an enhanced clustering system for the OpenBenches Android app, based on the clustering approach used by [OpenBenches.org](https://github.com/openbenches/openbenches.org). The clustering algorithm efficiently groups nearby benches into visual clusters to improve map performance and user experience.

## Key Features

### 1. **Grid-Based Clustering Algorithm**
- Uses a grid-based approach to reduce computational complexity from O(n²) to O(n log n)
- Groups benches by geographic grid cells before applying distance-based clustering
- Significantly improves performance with large datasets

### 2. **Dynamic Cluster Radius**
- Cluster radius varies based on zoom level:
  - **Very zoomed out** (< 8.0): 50km radius
  - **Very zoomed out** (< 10.0): 20km radius  
  - **Medium zoom** (< 12.0): 5km radius
  - **Lower zoom** (< 14.0): 1km radius
  - **Higher zoom** (< 15.0): 500m radius
  - **Very zoomed in** (≥ 15.0): 100m radius

### 3. **Enhanced Visual Design**
- **Color-coded clusters** based on bench count:
  - **Green** (< 10 benches): Small clusters
  - **Yellow** (10-49 benches): Medium clusters
  - **Orange** (50-99 benches): Large clusters
  - **Red** (100+ benches): Very large clusters
- **Enhanced cluster icons** with shadow effects and better typography
- **Compact display** for large numbers (e.g., "1k" for 1000+ benches)

### 4. **Performance Optimizations**
- **Efficient hash-based change detection** - only updates markers when bench data changes
- **Selective marker removal** - preserves user location marker during updates
- **Hardware acceleration** and tile preloading for smoother rendering
- **Optimized Haversine distance calculation** using `pow()` instead of multiplication

### 5. **Interactive Features**
- **Click clusters to zoom in** and reveal individual markers
- **Tap outside markers** to close popups
- **Smooth animations** when zooming to clusters
- **Proper popup management** - closes others when opening new ones

## Implementation Details

### Clustering Algorithm

```kotlin
fun clusterBenchesByDistance(benches: List<Bench>, zoomLevel: Double): List<List<Bench>> {
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
    val gridSize = clusterRadiusKm / 111.0 // Approximate km per degree
    val grid = mutableMapOf<String, MutableList<Bench>>()
    
    // Group benches by grid cell
    benches.forEach { bench ->
        val gridX = (bench.lng / gridSize).toInt()
        val gridY = (bench.lat / gridSize).toInt()
        val gridKey = "$gridX,$gridY"
        grid.getOrPut(gridKey) { mutableListOf() }.add(bench)
    }
    
    // Process each grid cell with distance-based clustering
    // ... implementation details
}
```

### Cluster Icon Creation

```kotlin
fun createClusterIcon(count: Int, context: Context): BitmapDrawable {
    // Determine color based on count
    val backgroundColor = when {
        count < 10 -> Color.parseColor("#4CAF50")  // Green
        count < 50 -> Color.parseColor("#FFC107") // Yellow
        count < 100 -> Color.parseColor("#FF9800") // Orange
        else -> Color.parseColor("#F44336")        // Red
    }
    
    // Create enhanced visual design with shadow effects
    // ... implementation details
}
```

### Marker Management

```kotlin
fun createMarkers(
    mapView: MapView,
    clusters: List<List<Bench>>,
    markerDrawable: BitmapDrawable,
    context: Context
) {
    clusters.forEach { cluster ->
        if (cluster.size == 1) {
            // Single bench - show individual marker
            // ... implementation
        } else {
            // Multiple benches - create cluster marker
            // ... implementation with zoom functionality
        }
    }
}
```

## Usage

The clustering is automatically applied when:
- **Zoom level < 16.0** (prevents clustering when very zoomed in)
- **Bench count > 10** (prevents unnecessary clustering for small datasets)

### Example Usage

```kotlin
@Composable
fun MapScreen(
    benches: List<Bench>,
    // ... other parameters
) {
    // Clustering is automatically handled by the MapScreen component
    // No additional configuration needed
}
```

## Testing

Comprehensive test coverage is provided in `MapClusteringTest.kt`:

- **Distance calculation tests** - verifies Haversine formula accuracy
- **Clustering algorithm tests** - validates clustering behavior with various datasets
- **Zoom level tests** - ensures proper cluster radius adjustment
- **Edge case tests** - handles single benches and widely separated benches

### Running Tests

```bash
./gradlew test
```

## Performance Benefits

1. **Reduced Marker Count**: Clustering reduces the number of markers rendered on the map
2. **Improved Rendering**: Fewer markers mean better map performance
3. **Better User Experience**: Clear visual feedback about bench density
4. **Efficient Updates**: Only updates markers when data changes
5. **Scalable**: Grid-based algorithm handles large datasets efficiently

## Comparison with OpenBenches.org

This implementation follows the same principles as OpenBenches.org:
- **Distance-based clustering** using Haversine formula
- **Zoom-dependent clustering** with dynamic radius
- **Color-coded visual feedback** for cluster sizes
- **Interactive zoom functionality** for cluster exploration

## Future Enhancements

Potential improvements for future versions:
- **Advanced clustering algorithms** (K-means, DBSCAN)
- **Custom cluster shapes** based on geographic boundaries
- **Cluster heatmaps** for density visualization
- **Performance profiling** and optimization
- **Accessibility improvements** for cluster interactions
