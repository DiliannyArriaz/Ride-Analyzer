package com.rideanalyzer.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.rideanalyzer.app.R
import com.rideanalyzer.app.model.TripInfo

class TripOverlay(private val context: Context) {
    private val windowManager = context.getSystemService<WindowManager>()
    private val prefs = context.getSharedPreferences("TripOverlayPrefs", Context.MODE_PRIVATE)
    private var overlayView: FrameLayout? = null
    private var bubbleContainer: LinearLayout? = null
    
    // Última posición conocida
    private var lastKnownX = 0
    private var lastKnownY = 0
    private var headerView: LinearLayout? = null
    private var valueView: TextView? = null
    private var timeView: TextView? = null
    private var distanceView: TextView? = null
    private var perMinuteView: TextView? = null
    private var perKmView: TextView? = null
    private var actionButton: TextView? = null
    private var expandButton: ImageView? = null
    private var statsContainer: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var autoHideRunnable: Runnable? = null
    var isShowing = false
        private set
    private var isExpanded = true // Default to expanded view
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var originalX = 0
    private var originalY = 0

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    init {
        // Initialize last known position from SharedPreferences
        lastKnownX = prefs.getInt("last_x", dpToPx(20))
        lastKnownY = prefs.getInt("last_y", dpToPx(100))
    }

    private fun createOverlayView(tripInfo: TripInfo) {
        // Create the main container with rounded corners and glass effect
        overlayView = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Create bubble container with adjusted dimensions
        bubbleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Increased width to accommodate $/min better and adjusted height
            layoutParams = FrameLayout.LayoutParams(dpToPx(160), if (isExpanded) dpToPx(280) else dpToPx(180))
        }

        // Create background with gradient and border
        val backgroundDrawable = GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#E6121212")) // Semi-transparent dark background
            setStroke(2, if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF0000")) // Neon border
        }
        bubbleContainer?.background = backgroundDrawable

        createHeader(tripInfo)
        createValueSection(tripInfo)
        createCompactStatsContainer(tripInfo)
        createStatsContainer(tripInfo)
        createActionButton(tripInfo)
        setupDragging()

        overlayView?.addView(bubbleContainer)
    }

    private fun createHeader(tripInfo: TripInfo) {
        headerView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4)) // Reduced padding
            background = ContextCompat.getDrawable(context,
                if (tripInfo.isProfitable) R.drawable.profitable_gradient_background
                else R.drawable.non_profitable_gradient_background
            )
        }

        // Platform badge
        val platformBadge = TextView(context).apply {
            text = tripInfo.platform ?: "Unknown"
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            // rounded badge background
            val badgeBg = GradientDrawable().apply {
                cornerRadius = dpToPx(10).toFloat() // Reduced corner radius
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

        // Expand/Collapse button with different icons to avoid confusion
        expandButton = ImageView(context).apply {
            // Use different icons for expand/collapse to avoid confusion with close
            setImageResource(if (isExpanded) android.R.drawable.arrow_down_float else android.R.drawable.arrow_up_float)
            setColorFilter(Color.WHITE)
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6)) // Reduced padding
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)) // Fixed size
            setOnClickListener {
                toggleExpanded()
            }
        }
        headerView?.addView(expandButton)

        bubbleContainer?.addView(headerView)
    }
    
    private fun createValueSection(tripInfo: TripInfo) {
        // Value section with glow effect
        val valueContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8)) // Reduced padding
            // inner rounded card
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, if (tripInfo.isProfitable) {
                intArrayOf(Color.parseColor("#1222FFFF"), Color.parseColor("#2E0F3F"))
            } else {
                intArrayOf(Color.parseColor("#40FF4444"), Color.parseColor("#40222A"))
            }).apply {
                cornerRadius = dpToPx(10).toFloat() // Reduced corner radius
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
        }

        val valueLabel = TextView(context).apply {
            text = if (tripInfo.isProfitable) "✓ RENTABLE" else "✕ NO RENTABLE"
            setTextColor(if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF6B6B"))
            textSize = 10f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        valueContainer.addView(valueLabel)

        valueView = TextView(context).apply {
            text = "$${String.format("%,.0f", tripInfo.price)}"
            setTextColor(if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF6B6B"))
            textSize = 28f // Reduced text size
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, 0) // Reduced padding
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        valueContainer.addView(valueView)

        bubbleContainer?.addView(valueContainer)
    }
    
    private fun createCompactStatsContainer(tripInfo: TripInfo) {
        // This container is only visible when the bubble is collapsed
        val compactStatsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4)) // Increased padding
            gravity = Gravity.CENTER // Center the content
        }
        
        // Time info with clock icon
        val timeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, dpToPx(8), 0) // Increased spacing between elements
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val clockIcon = TextView(context).apply {
            text = "⏰" // Simple clock emoji for now
            textSize = 10f
            setPadding(0, 0, dpToPx(2), 0)
        }
        timeContainer.addView(clockIcon)
        
        val timeText = TextView(context).apply {
            text = "${tripInfo.estimatedMinutes}min"
            setTextColor(Color.WHITE)
            textSize = 10f
        }
        timeContainer.addView(timeText)
        
        compactStatsContainer.addView(timeContainer)
        
        // Distance info with location icon
        val distanceContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(8), 0, dpToPx(8), 0) // Increased spacing between elements
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val locationIcon = TextView(context).apply {
            text = "📍" // Simple location emoji for now
            textSize = 10f
            setPadding(0, 0, dpToPx(2), 0)
        }
        distanceContainer.addView(locationIcon)
        
        val distanceText = TextView(context).apply {
            text = "${String.format("%.1f", tripInfo.distance)}km"
            setTextColor(Color.WHITE)
            textSize = 10f
        }
        distanceContainer.addView(distanceText)
        
        compactStatsContainer.addView(distanceContainer)
        
        // Price per minute with more space
        val pricePerMinContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(8), 0, 0, 0) // Increased spacing before this element
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val pricePerMinText = TextView(context).apply {
            text = "$${String.format("%.0f", tripInfo.pricePerMinute)}/min"
            setTextColor(Color.WHITE)
            textSize = 10f
        }
        pricePerMinContainer.addView(pricePerMinText)
        
        compactStatsContainer.addView(pricePerMinContainer)
        
        // Initially show/hide based on expanded state
        compactStatsContainer.visibility = if (isExpanded) View.GONE else View.VISIBLE
        
        bubbleContainer?.addView(compactStatsContainer)
    }

    private fun createStatsContainer(tripInfo: TripInfo) {
        statsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
        
        // Left column
        val leftColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, dpToPx(4), 0)
        }

        // Time card
        val timeCard = createDataCard("Tiempo", "${tripInfo.estimatedMinutes} min", "#2196F3")
        timeView = timeCard.findViewById(android.R.id.text1) as? TextView
        leftColumn.addView(timeCard)

        // Per minute card
        val perMinuteCard = createDataCard("\$/min", "$${String.format("%.0f", tripInfo.pricePerMinute)}", "#4CAF50")
        perMinuteView = perMinuteCard.findViewById(android.R.id.text1) as? TextView
        leftColumn.addView(perMinuteCard)

        statsContainer?.addView(leftColumn)

        // Right column
        val rightColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dpToPx(4), 0, 0, 0)
        }

        // Distance card
        val distanceCard = createDataCard("Distancia", "${String.format("%.1f", tripInfo.distance)} km", "#9C27B0")
        distanceView = distanceCard.findViewById(android.R.id.text1) as? TextView
        rightColumn.addView(distanceCard)

        // Per km card
        val perKmCard = createDataCard("\$/km", "$${String.format("%.0f", tripInfo.pricePerKm)}", "#E91E63")
        perKmView = perKmCard.findViewById(android.R.id.text1) as? TextView
        rightColumn.addView(perKmCard)

        statsContainer?.addView(rightColumn)
        bubbleContainer?.addView(statsContainer)
    }

    private fun createActionButton(tripInfo: TripInfo) {
        actionButton = TextView(context).apply {
            text = if (tripInfo.isProfitable) "⚡ ¡Viaje rentable!" else "✕ Rechazar"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundResource(if (tripInfo.isProfitable) 
                R.drawable.profitable_button_background 
            else 
                R.drawable.non_profitable_button_background)
            setOnClickListener { hide() }
        }
        bubbleContainer?.addView(actionButton)
    }

    private fun setupDragging() {
        bubbleContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        originalX = it.x
                        originalY = it.y
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                        params?.let {
                            val deltaX = event.rawX - dragStartX
                            val deltaY = event.rawY - dragStartY
                            it.x = (originalX + deltaX).toInt()
                            it.y = (originalY + deltaY).toInt()
                            windowManager?.updateViewLayout(overlayView, it)
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    // Save the new position
                    val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        lastKnownX = it.x
                        lastKnownY = it.y
                        prefs.edit()
                            .putInt("last_x", lastKnownX)
                            .putInt("last_y", lastKnownY)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }
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
        perMinuteView?.text = "$${String.format("%.0f", tripInfo.pricePerMinute)}"
        perKmView?.text = "$${String.format("%.0f", tripInfo.pricePerKm)}"
        actionButton?.text = if (tripInfo.isProfitable) "⚡ ¡Viaje rentable!" else "✕ Rechazar"
        
        // Update profitability indicator text
        val valueContainer = bubbleContainer?.getChildAt(1) as? LinearLayout
        val valueLabel = valueContainer?.getChildAt(0) as? TextView
        valueLabel?.text = if (tripInfo.isProfitable) "✓ RENTABLE" else "✕ NO RENTABLE"
        
        // Update colors based on profitability
        val textColor = if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF6B6B")
        valueView?.setTextColor(textColor)
        valueLabel?.setTextColor(textColor)

        // Update header background (gradient)
        headerView?.background = ContextCompat.getDrawable(context,
            if (tripInfo.isProfitable) R.drawable.profitable_gradient_background
            else R.drawable.non_profitable_gradient_background
        )

        // Update platform badge color
        val platformBadge = headerView?.getChildAt(0) as? TextView
        platformBadge?.let {
            val badgeBg = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(if (tripInfo.isProfitable) Color.parseColor("#00BBBB") else Color.parseColor("#FF6B6B"))
            }
            it.background = badgeBg
        }

        // Update border/background of the overlay
        val backgroundDrawable = GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#E6000000")) // darker glass
            setStroke(dpToPx(2), if (tripInfo.isProfitable) Color.parseColor("#00FFFF") else Color.parseColor("#FF6B6B"))
        }
        bubbleContainer?.background = backgroundDrawable

        // Update action button background (drawables kept, create them if missing)
        actionButton?.setBackgroundResource(if (tripInfo.isProfitable)
            R.drawable.profitable_button_background
        else
            R.drawable.non_profitable_button_background)
        
        Log.d(TAG, "Trip overlay updated with new data: $tripInfo")
    }

    fun showTripAnalysis(tripInfo: TripInfo) {
        // Cancel any pending auto-hide when a new trip is detected
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        
        if (isShowing) {
            // Update the existing overlay instead of hiding and showing
            updateOverlayView(tripInfo)
        } else {
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

            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay", e)
            }
        }
        
        // Don't schedule auto-hide here - it should only happen when trip is no longer visible
    }
    
    // New method to handle when trip is no longer visible
    fun onTripNoLongerVisible() {
        Log.d(TAG, "Trip no longer visible, scheduling auto-hide in 3 seconds")
        scheduleAutoHide()
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

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        // Update the expand button icon with clearer expand/collapse indicators
        expandButton?.setImageResource(if (isExpanded) android.R.drawable.arrow_down_float else android.R.drawable.arrow_up_float)
        
        // Show/hide the appropriate stats container
        // Find the compact stats container (index 2) and regular stats container (index 3)
        if (bubbleContainer?.childCount ?: 0 > 3) {
            val compactStats = bubbleContainer?.getChildAt(2)
            val regularStats = bubbleContainer?.getChildAt(3)
            
            compactStats?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            regularStats?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
        
        // Update the bubble container dimensions
        bubbleContainer?.layoutParams?.height = if (isExpanded) dpToPx(280) else dpToPx(180)
        bubbleContainer?.layoutParams?.width = dpToPx(160) // Consistent width
        overlayView?.requestLayout()
        
        Log.d(TAG, "Bubble expanded state changed to: $isExpanded")
    }

    // New method to create an overlay view that shows all detected text
    private fun createTextOverlayView(detectedText: String) {
        overlayView = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            setBackgroundColor(Color.parseColor("#E0000000")) // Semi-transparent black
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        val titleView = TextView(context).apply {
            text = "ML Kit Text Detection"
            setTextColor(Color.parseColor("#00FFFF")) // Cyan color
            textSize = 18f
            gravity = Gravity.CENTER
        }
        textContainer.addView(titleView)

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
        textContainer.addView(detailsView)

        // Close button
        val closeButton = TextView(context).apply {
            text = "✕ Cerrar"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(15), 0, 0)
            setOnClickListener { hide() }
        }
        textContainer.addView(closeButton)
        
        overlayView?.addView(textContainer)
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
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastKnownX // Position from last known position
            y = lastKnownY // Position from last known position
        }
    }

    private fun scheduleAutoHide() {
        // Cancel any existing auto-hide runnable
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        
        // Create new auto-hide runnable
        autoHideRunnable = Runnable {
            Log.d(TAG, "Auto-hiding overlay after 3 seconds of trip not being visible")
            hide()
        }
        
        // Schedule the auto-hide for 3 seconds
        autoHideRunnable?.let { handler.postDelayed(it, 3000) }
    }

    fun hide() {
        // Remove any pending hide operations
        handler.removeCallbacks(hideRunnable)
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        
        if (isShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
                isShowing = false
                overlayView = null
                bubbleContainer = null
                headerView = null
                valueView = null
                timeView = null
                distanceView = null
                perMinuteView = null
                perKmView = null
                actionButton = null
                expandButton = null
                statsContainer = null
                autoHideRunnable = null
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