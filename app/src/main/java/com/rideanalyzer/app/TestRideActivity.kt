package com.rideanalyzer.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TestRideActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TestRideActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "TestRideActivity created")
        
        // Create a simple UI that simulates a ride offer
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }
        
        // Add title
        val title = TextView(this).apply {
            text = "Uber Driver"
            textSize = 24f
            setTextColor(Color.BLACK)
            setContentDescription("uber")
            // Make it accessible
            isFocusable = true
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
        layout.addView(title)
        
        // Add simulated trip information
        val priceText = TextView(this).apply {
            text = "ARS15,200"
            textSize = 20f
            setTextColor(Color.BLACK)
            setContentDescription("ARS15,200")
            // Make it accessible
            isFocusable = true
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
        layout.addView(priceText)
        
        val distanceText = TextView(this).apply {
            text = "A 12 min (5,2 km)"
            textSize = 18f
            setTextColor(Color.BLACK)
            setContentDescription("A 12 min (5,2 km)")
            // Make it accessible
            isFocusable = true
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
        layout.addView(distanceText)
        
        val tripText = TextView(this).apply {
            text = "Viaje: 28 min (12,3 km)"
            textSize = 18f
            setTextColor(Color.BLACK)
            setContentDescription("Viaje: 28 min (12,3 km)")
            // Make it accessible
            isFocusable = true
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
        layout.addView(tripText)
        
        val ratingText = TextView(this).apply {
            text = "4.85 (312)"
            textSize = 16f
            setTextColor(Color.BLACK)
            setContentDescription("4.85 (312)")
            // Make it accessible
            isFocusable = true
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
        layout.addView(ratingText)
        
        // Add a button to simulate accepting the ride
        val acceptButton = Button(this).apply {
            text = "Aceptar Viaje"
            setOnClickListener {
                Log.d(TAG, "Accept button clicked")
            }
            // Make it accessible
            isFocusable = true
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
        layout.addView(acceptButton)
        
        setContentView(layout)
    }
}