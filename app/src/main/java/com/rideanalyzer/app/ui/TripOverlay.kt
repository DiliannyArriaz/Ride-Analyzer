package com.rideanalyzer.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import com.rideanalyzer.app.model.TripInfo

class TripOverlay(private val context: Context) {
    private val windowManager = context.getSystemService<WindowManager>()
    private var overlayView: LinearLayout? = null
    private var titleView: TextView? = null
    private var detailsView: TextView? = null
    private var recommendationView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    var isShowing = false
        private set

    fun showTripAnalysis(tripInfo: TripInfo) {
        if (isShowing) {
            // Update the existing overlay instead of hiding and showing
            updateOverlayView(tripInfo)
            return
        }

        // Check if we have overlay permission
        if (!hasOverlayPermission()) {
            Log.e(TAG, "Overlay permission not granted")
            return
        }

        createOverlayView(tripInfo)
        val params = createLayoutParams()

        try {
            windowManager?.addView(overlayView, params)
            isShowing = true

            Log.d(TAG, "Trip overlay displayed: $tripInfo")

            // Don't auto-hide - keep it on screen
            // handler.postDelayed(hideRunnable, 10000)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    // New method to show all detected text for testing purposes
    fun showAllDetectedText(detectedText: String) {
        if (isShowing) {
            // Update the existing overlay instead of hiding and showing
            updateTextOverlayView(detectedText)
            return
        }

        // Check if we have overlay permission
        if (!hasOverlayPermission()) {
            Log.e(TAG, "Overlay permission not granted")
            return
        }

        createTextOverlayView(detectedText)
        val params = createLayoutParams()

        try {
            windowManager?.addView(overlayView, params)
            isShowing = true

            Log.d(TAG, "Text overlay displayed with detected text")

            // Don't auto-hide - keep it on screen
            // handler.postDelayed(hideRunnable, 10000)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing text overlay", e)
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true // Overlay permission not needed before Android M
        }
    }

    private fun createOverlayView(tripInfo: TripInfo) {
        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
            setBackgroundColor(Color.parseColor("#E0000000")) // Semi-transparent black
        }

        // Platform and profitability indicator (matching original format)
        val profitabilityText = if (tripInfo.isProfitable) "✓ RENTABLE" else "✗ NO RENTABLE"
        val titleColor = if (tripInfo.isProfitable) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")

        titleView = TextView(context).apply {
            text = "${tripInfo.platform} - $profitabilityText"
            setTextColor(titleColor)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        overlayView?.addView(titleView)

        // Trip details with improved format
        val detailsText = buildString {
            append("Precio: ${tripInfo.currency}${String.format("%.0f", tripInfo.price)}\n")
            append("Distancia: ${String.format("%.1f", tripInfo.distance)} ${tripInfo.distanceUnit}\n")
            if (tripInfo.estimatedMinutes > 0) {
                append("Tiempo: ${tripInfo.estimatedMinutes} min\n")
            }
            tripInfo.rating?.let { rating ->
                append("Rating: ${String.format("%.1f", rating)}\n")
            }
            if (tripInfo.price > 0 && tripInfo.distance > 0) {
                append("Precio/km: ${tripInfo.currency}${String.format("%.2f", tripInfo.price / tripInfo.distance)}\n")
            }
            if (tripInfo.price > 0 && tripInfo.estimatedMinutes > 0) {
                append("Precio/min: ${tripInfo.currency}${String.format("%.2f", tripInfo.price / tripInfo.estimatedMinutes)}")
            }
        }

        detailsView = TextView(context).apply {
            text = detailsText.trim()
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        overlayView?.addView(detailsView)

        // Recommendation (matching original format)
        val recommendation = if (tripInfo.isProfitable) "✓ Recomendado aceptar este viaje" else "✗ Este viaje podría no ser rentable"
        recommendationView = TextView(context).apply {
            text = recommendation
            setTextColor(titleColor)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }
        overlayView?.addView(recommendationView)

        // Close button
        val closeButton = TextView(context).apply {
            text = "✕ Cerrar"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 0)
            setOnClickListener { hide() }
        }
        overlayView?.addView(closeButton)
    }

    private fun updateOverlayView(tripInfo: TripInfo) {
        // Update the existing views with new data
        val profitabilityText = if (tripInfo.isProfitable) "✓ RENTABLE" else "✗ NO RENTABLE"
        val titleColor = if (tripInfo.isProfitable) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        
        titleView?.text = "${tripInfo.platform} - $profitabilityText"
        titleView?.setTextColor(titleColor)

        // Trip details with improved format
        val detailsText = buildString {
            append("Precio: ${tripInfo.currency}${String.format("%.0f", tripInfo.price)}\n")
            append("Distancia: ${String.format("%.1f", tripInfo.distance)} ${tripInfo.distanceUnit}\n")
            if (tripInfo.estimatedMinutes > 0) {
                append("Tiempo: ${tripInfo.estimatedMinutes} min\n")
            }
            tripInfo.rating?.let { rating ->
                append("Rating: ${String.format("%.1f", rating)}\n")
            }
            if (tripInfo.price > 0 && tripInfo.distance > 0) {
                append("Precio/km: ${tripInfo.currency}${String.format("%.2f", tripInfo.price / tripInfo.distance)}\n")
            }
            if (tripInfo.price > 0 && tripInfo.estimatedMinutes > 0) {
                append("Precio/min: ${tripInfo.currency}${String.format("%.2f", tripInfo.price / tripInfo.estimatedMinutes)}")
            }
        }

        detailsView?.text = detailsText.trim()

        // Recommendation (matching original format)
        val recommendation = if (tripInfo.isProfitable) "✓ Recomendado aceptar este viaje" else "✗ Este viaje podría no ser rentable"
        recommendationView?.text = recommendation
        recommendationView?.setTextColor(titleColor)
        
        Log.d(TAG, "Trip overlay updated with new data: $tripInfo")
    }

    // New method to create an overlay view that shows all detected text
    private fun createTextOverlayView(detectedText: String) {
        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
            setBackgroundColor(Color.parseColor("#E0000000")) // Semi-transparent black
        }

        // Title
        val titleView = TextView(context).apply {
            text = "ML Kit Text Detection"
            setTextColor(Color.parseColor("#4CAF50")) // Green color
            textSize = 18f
            gravity = Gravity.CENTER
        }
        overlayView?.addView(titleView)

        // Detected text (limit to first 500 characters to avoid overwhelming the display)
        val displayText = if (detectedText.length > 500) {
            detectedText.substring(0, 500) + "\n\n... (text truncated)"
        } else {
            detectedText
        }

        detailsView = TextView(context).apply {
            text = displayText
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        overlayView?.addView(detailsView)

        // Close button
        val closeButton = TextView(context).apply {
            text = "✕ Cerrar"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 0)
            setOnClickListener { hide() }
        }
        overlayView?.addView(closeButton)
    }

    private fun updateTextOverlayView(detectedText: String) {
        // Update the existing text view with new data
        val displayText = if (detectedText.length > 500) {
            detectedText.substring(0, 500) + "\n\n... (text truncated)"
        } else {
            detectedText
        }
        
        detailsView?.text = displayText
        Log.d(TAG, "Text overlay updated with new data")
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200 // Distance from top
        }
    }

    fun hide() {
        // Remove any pending hide operations
        handler.removeCallbacks(hideRunnable)
        
        if (isShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
                isShowing = false
                overlayView = null
                titleView = null
                detailsView = null
                recommendationView = null
                Log.d(TAG, "Trip overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay", e)
            }
        }
    }
    
    fun postDelayed(runnable: Runnable, delayMillis: Long) {
        handler.postDelayed(runnable, delayMillis)
    }
    
    fun removeCallbacks(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }

    companion object {
        private const val TAG = "TripOverlay"
    }
}