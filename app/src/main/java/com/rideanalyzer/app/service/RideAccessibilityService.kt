package com.rideanalyzer.app.service

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rideanalyzer.app.analyzer.TripAnalyzer
import com.rideanalyzer.app.model.TripInfo
import com.rideanalyzer.app.ui.TripOverlay
import com.rideanalyzer.app.util.RideAppTextExtractor
import android.content.Context

class RideAccessibilityService : AccessibilityService() {
    
    private lateinit var tripAnalyzer: TripAnalyzer
    private lateinit var tripOverlay: TripOverlay
    private lateinit var rideAppTextExtractor: RideAppTextExtractor
    private lateinit var sharedPreferences: SharedPreferences
    private var isMonitoring = false
    private var lastPackageName: String? = null
    private var lastUpdateTime: Long = 0
    private val MIN_UPDATE_INTERVAL = 200L // Reduced to 200ms for faster updates when multiple trips are present
    private var lastTripInfo: TripInfo? = null // Cache last trip info to detect changes
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        tripAnalyzer = TripAnalyzer(this)
        tripOverlay = TripOverlay(this)
        rideAppTextExtractor = RideAppTextExtractor()
        sharedPreferences = getSharedPreferences("RideAnalyzerPrefs", Context.MODE_PRIVATE)
        
        // Load the desired hourly rate from SharedPreferences
        val desiredHourlyRate = sharedPreferences.getFloat("desired_hourly_rate", 10000f).toDouble()
        tripAnalyzer.desiredHourlyRate = desiredHourlyRate
        Log.d(TAG, "RideAccessibilityService connected with desired hourly rate: $desiredHourlyRate")
        
        isMonitoring = true // Start monitoring automatically when service is connected
        Log.d(TAG, "RideAccessibilityService connected and monitoring started")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isMonitoring) return
        
        if (event == null) return
        
        // Check if we're in Uber or DiDi app (more specific package names)
        val packageName = event.packageName?.toString() ?: return
        
        val isRideApp = isTargetRideApp(packageName)
        
        // Update last package name
        lastPackageName = packageName
        
        if (isRideApp) {
            Log.d(TAG, "Detected target ride app: $packageName")
            if (packageName.startsWith("com.didiglobal.driver")) {
                Log.d(TAG, "Detected DiDi app specifically: $packageName")
            }
            
            // Rate limiting - only update at most every MIN_UPDATE_INTERVAL ms
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime < MIN_UPDATE_INTERVAL) {
                Log.d(TAG, "Skipping update - rate limited (${currentTime - lastUpdateTime}ms since last update)")
                return
            }
            lastUpdateTime = currentTime
            
            // Update the desired hourly rate from SharedPreferences
            val desiredHourlyRate = sharedPreferences.getFloat("desired_hourly_rate", 10000f).toDouble()
            tripAnalyzer.desiredHourlyRate = desiredHourlyRate
            Log.d(TAG, "Updated desired hourly rate: $desiredHourlyRate")
            
            // Extract trip information from UI hierarchy using the specialized extractor
            val tripInfo = extractTripInfoFromUI(rootInActiveWindow, packageName)
            if (tripInfo != null && tripInfo.isValid()) {
                Log.d(TAG, "Valid trip info extracted from $packageName: $tripInfo")
                
                // Check if this is a new or significantly different trip
                if (isSignificantlyDifferentTrip(lastTripInfo, tripInfo)) {
                    Log.d(TAG, "Detected new or significantly different trip, updating overlay")
                    tripInfo.isProfitable = tripAnalyzer.calculateProfitability(tripInfo)
                    tripOverlay.showTripAnalysis(tripInfo)
                    lastTripInfo = tripInfo.copy() // Store a copy to compare with next trip
                } else {
                    Log.d(TAG, "Trip info is similar to last trip, skipping overlay update")
                    // Still update profitability in case desired hourly rate changed
                    tripInfo.isProfitable = tripAnalyzer.calculateProfitability(tripInfo)
                    // Update overlay with new profitability but same basic info
                    tripOverlay.showTripAnalysis(tripInfo)
                    lastTripInfo = tripInfo.copy()
                }
            } else {
                Log.d(TAG, "No valid trip info found in $packageName")
                // Only hide overlay if we had a valid trip before
                if (lastTripInfo != null) {
                    // Notify overlay that trip is no longer visible
                    tripOverlay.onTripNoLongerVisible()
                    lastTripInfo = null
                }
            }
        } else {
            // Only hide overlay when switching away from ride apps
            if (lastPackageName != packageName) {
                Log.d(TAG, "Switching away from ride app, notifying overlay")
                tripOverlay.onTripNoLongerVisible()
                lastTripInfo = null
            }
        }
    }

    /**
     * Check if the new trip is significantly different from the last one
     * This helps us detect when a new trip appears (e.g., after rejecting one)
     */
    private fun isSignificantlyDifferentTrip(lastTrip: TripInfo?, newTrip: TripInfo): Boolean {
        // If there was no previous trip, this is definitely new
        if (lastTrip == null) return true
        
        // Check if key trip parameters have changed significantly
        val priceChanged = Math.abs(newTrip.price - lastTrip.price) > 100 // More than 100 ARS difference
        val distanceChanged = Math.abs(newTrip.distance - lastTrip.distance) > 1.0 // More than 1km difference
        val timeChanged = Math.abs(newTrip.estimatedMinutes - lastTrip.estimatedMinutes) > 5 // More than 5 minutes difference
        
        Log.d(TAG, "Trip comparison - Price changed: $priceChanged, Distance changed: $distanceChanged, Time changed: $timeChanged")
        
        return priceChanged || distanceChanged || timeChanged
    }

    /**
     * Check if the package is a target ride app (Uber or DiDi)
     */
    private fun isTargetRideApp(packageName: String): Boolean {
        // List of known Uber and DiDi package names
        val uberPackages = listOf(
            "com.ubercab",
            "com.ubercab.driver",
        )
        
        val didiPackages = listOf(
            "com.didiglobal.driver",
            "com.didi.sd.passenger",
            "com.didi.sdk.passenger",
            "com.didi.driver"
        )
        
        // Also include test app
        val isTestApp = packageName.contains("rideanalyzer")
        
        val isUberApp = uberPackages.any { packageName.startsWith(it) }
        val isDidiApp = didiPackages.any { packageName.startsWith(it) }
        
        Log.d(TAG, "Package check - $packageName: Uber=$isUberApp, DiDi=$isDidiApp, Test=$isTestApp")
        
        return isUberApp || isDidiApp || isTestApp
    }
    
    private fun extractTripInfoFromUI(nodeInfo: AccessibilityNodeInfo?, packageName: String): TripInfo? {
        nodeInfo?.let {
            // Collect all text from the UI hierarchy
            val allText = collectAllText(it)
            
            if (allText.isNotBlank()) {
                // Use the specialized extractor for Uber/DiDi apps
                return rideAppTextExtractor.extractTripInfoFromRideApp(allText, packageName)
            }
        }
        
        return null
    }
    
    private fun collectAllText(node: AccessibilityNodeInfo): String {
        val textBuilder = StringBuilder()
        traverseNodes(node) { node ->
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            text?.let {
                if (it.isNotBlank() && it.length > 1) {
                    textBuilder.append(it).append("\n")
                }
            }
        }
        return textBuilder.toString()
    }
    
    private fun traverseNodes(node: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        action(node)
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNodes(child, action)
            }
        }
    }
    
    fun startMonitoring() {
        isMonitoring = true
        Log.d(TAG, "Accessibility service monitoring started")
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        tripOverlay.hide()
        Log.d(TAG, "Accessibility service monitoring stopped")
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "RideAccessibilityService interrupted")
    }
    
    companion object {
        private const val TAG = "RideAccessibilityService"
    }
}