package com.rideanalyzer.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.rideanalyzer.app.R
import com.rideanalyzer.app.model.TripInfo

class TripOverlay(private val context: Context) {
    private val windowManager = context.getSystemService<WindowManager>()
    private var overlayView: LinearLayout? = null
    private var headerView: LinearLayout? = null
    private var valueView: TextView? = null
    private var timeView: TextView? = null
    private var distanceView: TextView? = null
    private var perMinuteView: TextView? = null
    private var perKmView: TextView? = null
    private var actionButton: TextView? = null
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
        // Create the main container with rounded corners and glass effect
        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Set size to match the reference design rectangle
            layoutParams = android.view.ViewGroup.LayoutParams(dpToPx(180), dpToPx(180))
        }

        // Create background with gradient and border
        val backgroundDrawable = GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#E6121212")) // Semi-transparent dark background
            setStroke(2, if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF0000")) // Neon border
        }
        overlayView?.background = backgroundDrawable

        // Create header with platform and profitability indicator
        headerView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }

        // Create gradient background for header (neon cyan->purple when profitable, red->pink when not)
        val headerBackground = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, if (tripInfo.isProfitable) {
            intArrayOf(Color.parseColor("#1033FFFF"), Color.parseColor("#8033AAFF"))
        } else {
            intArrayOf(Color.parseColor("#40FF4444"), Color.parseColor("#FF6677"))
        }).apply {
            cornerRadius = dpToPx(8).toFloat()
        }
        headerView?.background = headerBackground

        // Platform badge
        val platformBadge = TextView(context).apply {
            text = tripInfo.platform ?: "Unknown"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            // rounded badge background
            val badgeBg = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(if (tripInfo.isProfitable) Color.parseColor("#00BBBB") else Color.parseColor("#FF6B6B"))
            }
            background = badgeBg
        }
        headerView?.addView(platformBadge)

        // Spacer
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        headerView?.addView(spacer)

        // Profitability indicator
        val profitabilityIndicator = TextView(context).apply {
            text = if (tripInfo.isProfitable) "RENTABLE" else "NO RENTABLE"
            setTextColor(if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF0000"))
            textSize = 10f
            setPadding(dpToPx(4), 0, 0, 0)
        }
        headerView?.addView(profitabilityIndicator)

        overlayView?.addView(headerView)

        // Value section with glow effect
        val valueContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            // inner rounded card
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, if (tripInfo.isProfitable) {
                intArrayOf(Color.parseColor("#1222FFFF"), Color.parseColor("#2E0F3F"))
            } else {
                intArrayOf(Color.parseColor("#40FF4444"), Color.parseColor("#40222A"))
            }).apply {
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
        }

        val valueLabel = TextView(context).apply {
            text = "VALOR"
            setTextColor(Color.parseColor("#B3FFFFFF")) // White with 70% opacity
            textSize = 10f
            gravity = Gravity.CENTER
        }
        valueContainer.addView(valueLabel)

        valueView = TextView(context).apply {
            text = "$${String.format("%,.0f", tripInfo.price)}"
            setTextColor(if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF6B6B"))
            textSize = 34f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(6), 0, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        valueContainer.addView(valueView)

        overlayView?.addView(valueContainer)

        // Grid of data with icons
        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        }

        // Left column
        val leftColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, dpToPx(4), 0)
        }

        // Time card
        val timeCard = createDataCard("Tiempo", "${tripInfo.estimatedMinutes} min", "#2196F3")
        timeView = timeCard.findViewById<TextView>(android.R.id.text1)
        leftColumn.addView(timeCard)

        // Per minute card
        val perMinuteCard = createDataCard("$/min", "$${tripInfo.pricePerMinute.toInt()}", "#4CAF50")
        perMinuteView = perMinuteCard.findViewById<TextView>(android.R.id.text1)
        leftColumn.addView(perMinuteCard)

        gridContainer.addView(leftColumn)

        // Right column
        val rightColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dpToPx(4), 0, 0, 0)
        }

        // Distance card
        val distanceCard = createDataCard("Distancia", "${String.format("%.1f", tripInfo.distance)} km", "#9C27B0")
        distanceView = distanceCard.findViewById<TextView>(android.R.id.text1)
        rightColumn.addView(distanceCard)

        // Per km card
        val perKmCard = createDataCard("$/km", "$${tripInfo.pricePerKm.toInt()}", "#E91E63")
        perKmView = perKmCard.findViewById<TextView>(android.R.id.text1)
        rightColumn.addView(perKmCard)

        gridContainer.addView(rightColumn)

        overlayView?.addView(gridContainer)

        // Action button with neon effect
        actionButton = TextView(context).apply {
            text = if (tripInfo.isProfitable) "⚡ ¡Viaje rentable!" else "✕ Rechazar"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            setBackgroundResource(if (tripInfo.isProfitable) 
                R.drawable.profitable_button_background 
            else 
                R.drawable.non_profitable_button_background)
            setOnClickListener { hide() }
        }
        overlayView?.addView(actionButton)
    }

    private fun createDataCard(label: String, value: String, color: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(4))
            }
            
            // Background with border
            val background = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#801E1E1E")) // Dark background with transparency
                setStroke(1, Color.parseColor("#4D757575")) // Gray border
            }
            setBackground(background)
            
            // Label
            val labelView = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor(color))
                textSize = 11f
                setPadding(0, 0, 0, dpToPx(4))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            addView(labelView)
            
            // Value
            val valueView = TextView(context).apply {
                id = android.R.id.text1
                text = value
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dpToPx(4))
            }
            addView(valueView)
        }
    }

    private fun updateOverlayView(tripInfo: TripInfo) {
        // Update all the views with new data
        valueView?.text = "$${String.format("%,.0f", tripInfo.price)}"
        timeView?.text = "${tripInfo.estimatedMinutes} min"
        distanceView?.text = "${String.format("%.1f", tripInfo.distance)} km"
        perMinuteView?.text = "$${tripInfo.pricePerMinute.toInt()}"
        perKmView?.text = "$${tripInfo.pricePerKm.toInt()}"
        actionButton?.text = if (tripInfo.isProfitable) "⚡ ¡Viaje rentable!" else "✕ Rechazar"
        
        // Update colors based on profitability
        val textColor = if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF6B6B")
        valueView?.setTextColor(textColor)

        // Update header background (gradient)
        val headerBg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, if (tripInfo.isProfitable) {
            intArrayOf(Color.parseColor("#1033FFFF"), Color.parseColor("#8033AAFF"))
        } else {
            intArrayOf(Color.parseColor("#40FF4444"), Color.parseColor("#FF6677"))
        }).apply { cornerRadius = dpToPx(8).toFloat() }
        headerView?.background = headerBg

        // Update border/background of the overlay
        val backgroundDrawable = GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#E6000000")) // darker glass
            setStroke(dpToPx(2), if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF6B6B"))
        }
        overlayView?.background = backgroundDrawable

        // Update action button background (drawables kept, create them if missing)
        actionButton?.setBackgroundResource(if (tripInfo.isProfitable)
            R.drawable.profitable_button_background
        else
            R.drawable.non_profitable_button_background)
        
        Log.d(TAG, "Trip overlay updated with new data: $tripInfo")
    }

    // New method to create an overlay view that shows all detected text
    private fun createTextOverlayView(detectedText: String) {
        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            setBackgroundColor(Color.parseColor("#E0000000")) // Semi-transparent black
        }

        // Title
        val titleView = TextView(context).apply {
            text = "ML Kit Text Detection"
            setTextColor(Color.parseColor("#00FFFF")) // Cyan color
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

        val detailsView = TextView(context).apply {
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
            setPadding(0, dpToPx(15), 0, 0)
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
        
        // Find the details view in the overlay
        if (overlayView?.childCount ?: 0 > 1) {
            val detailsView = overlayView?.getChildAt(1) as? TextView
            detailsView?.text = displayText
        }
        
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
        dpToPx(180), // Width to match reference rectangle
        dpToPx(180), // Square height to match reference rectangle
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20) // Position from left
            y = dpToPx(100) // Position from top
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun hide() {
        // Remove any pending hide operations
        handler.removeCallbacks(hideRunnable)
        
        if (isShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
                isShowing = false
                overlayView = null
                headerView = null
                valueView = null
                timeView = null
                distanceView = null
                perMinuteView = null
                perKmView = null
                actionButton = null
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