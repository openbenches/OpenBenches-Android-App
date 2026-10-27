package org.openbenches.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.sqrt

/**
 * Fires [onHold] when the user keeps a finger still on the map for [holdDurationMs].
 * Does not consume touch events, so panning/zooming still work.
 */
class MapLongHoldOverlay(
    private val holdDurationMs: Long = 3_000L,
    private val moveCancelPx: Float = 48f,
    private val onHold: (GeoPoint) -> Unit,
) : Overlay() {
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private var pending: Runnable? = null

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        if (event == null || mapView == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPending()
                downX = event.x
                downY = event.y
                fired = false
                val x = downX
                val y = downY
                pending = Runnable {
                    if (!fired) {
                        fired = true
                        val geo = mapView.projection.fromPixels(x.toInt(), y.toInt()) as GeoPoint
                        onHold(geo)
                    }
                }
                handler.postDelayed(pending!!, holdDurationMs)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (sqrt(dx * dx + dy * dy) > moveCancelPx) {
                    cancelPending()
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            -> cancelPending()
        }
        return false
    }

    private fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    override fun onDetach(mapView: MapView?) {
        cancelPending()
        super.onDetach(mapView)
    }
}

