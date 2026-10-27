package org.openbenches.ui

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * Base map style for osmdroid views. OpenStreetMap street map and Esri World Imagery
 * (satellite), matching openbenches.org.
 */
enum class MapProvider {
    OSM,
    ESRI_SATELLITE;

    fun menuLabel(): String = when (this) {
        OSM -> "Street map (OpenStreetMap)"
        ESRI_SATELLITE -> "Satellite (Esri)"
    }

    companion object {
        fun fromStoredValue(value: String?): MapProvider =
            entries.find { it.name == value } ?: OSM
    }
}

object MapTileSources {
    /** Esri uses Z/Y/X tile ordering, not the usual Z/X/Y slippy-map layout. */
    private val esriWorldImagery: ITileSource by lazy {
        object : OnlineTileSourceBase(
            "ESRI World Imagery",
            0,
            19,
            256,
            "",
            arrayOf(
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
            ),
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String =
                getBaseUrl() +
                    MapTileIndex.getZoom(pMapTileIndex) + "/" +
                    MapTileIndex.getY(pMapTileIndex) + "/" +
                    MapTileIndex.getX(pMapTileIndex) +
                    mImageFilenameEnding
        }
    }

    fun tileSourceFor(provider: MapProvider): ITileSource = when (provider) {
        MapProvider.OSM -> TileSourceFactory.MAPNIK
        MapProvider.ESRI_SATELLITE -> esriWorldImagery
    }
}
