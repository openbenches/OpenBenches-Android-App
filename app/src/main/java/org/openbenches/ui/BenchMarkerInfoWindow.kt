package org.openbenches.ui

import android.widget.TextView
import org.openbenches.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * Bench marker popup with title and description at the same text size.
 * Tapping the popup opens bench details (same as a second tap on the marker).
 */
class BenchMarkerInfoWindow(
    mapView: MapView,
    private val onBenchClick: (benchId: Int) -> Unit,
) : InfoWindow(R.layout.bench_marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        mView.findViewById<TextView>(R.id.bench_info_title)?.text = marker.title.orEmpty()
        mView.findViewById<TextView>(R.id.bench_info_description)?.text =
            marker.subDescription.orEmpty()

        val benchId = marker.relatedObject as? Int
        mView.isClickable = benchId != null
        mView.setOnClickListener {
            if (benchId != null) {
                onBenchClick(benchId)
            }
        }
    }

    override fun onClose() {
        mView.setOnClickListener(null)
        mView.isClickable = false
    }
}
