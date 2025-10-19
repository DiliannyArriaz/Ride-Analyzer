package com.rideanalyzer.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rideanalyzer.app.service.ScreenshotService

class ScreenshotReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            START_CAPTURE_ACTION -> {
                Log.d(TAG, "Start capture action received")
                // Start the screenshot service
                val serviceIntent = Intent(context, ScreenshotService::class.java)
                context.startService(serviceIntent)
            }
            
            STOP_CAPTURE_ACTION -> {
                Log.d(TAG, "Stop capture action received")
                // Stop the screenshot service
                val serviceIntent = Intent(context, ScreenshotService::class.java)
                context.stopService(serviceIntent)
            }
            
            CAPTURE_SCREENSHOT_ACTION -> {
                Log.d(TAG, "Capture screenshot action received")
                // Trigger a single screenshot capture
                // This would be implemented in a more complete version
            }
        }
    }
    
    companion object {
        private const val TAG = "ScreenshotReceiver"
        const val START_CAPTURE_ACTION = "com.rideanalyzer.app.START_CAPTURE"
        const val STOP_CAPTURE_ACTION = "com.rideanalyzer.app.STOP_CAPTURE"
        const val CAPTURE_SCREENSHOT_ACTION = "com.rideanalyzer.app.CAPTURE_SCREENSHOT"
    }
}