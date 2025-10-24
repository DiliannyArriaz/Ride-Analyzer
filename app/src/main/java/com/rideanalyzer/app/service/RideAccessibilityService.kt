package com.rideanalyzer.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rideanalyzer.app.analyzer.TripAnalyzer
import com.rideanalyzer.app.model.TripInfo
import com.rideanalyzer.app.ui.TripOverlay
import com.rideanalyzer.app.util.RideAppTextExtractor

class RideAccessibilityService : AccessibilityService() {
    
    private lateinit var tripAnalyzer: TripAnalyzer
    private lateinit var tripOverlay: TripOverlay
    private lateinit var rideAppTextExtractor: RideAppTextExtractor
    private var isMonitoring = false
    private var lastPackageName: String? = null
    private var lastUpdateTime: Long = 0
    private val MIN_UPDATE_INTERVAL = 300L // Increased to 300ms to reduce system load on mid to low-end devices
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        tripAnalyzer = TripAnalyzer(this)
        tripOverlay = TripOverlay(this)
        rideAppTextExtractor = RideAppTextExtractor()
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
            
            // Extract trip information from UI hierarchy using the specialized extractor
            val tripInfo = extractTripInfoFromUI(rootInActiveWindow, packageName)
            if (tripInfo != null && tripInfo.isValid()) {
                Log.d(TAG, "Valid trip info extracted from $packageName: $tripInfo")
                tripInfo.isProfitable = tripAnalyzer.calculateProfitability(tripInfo)
                Log.d(TAG, "Calling overlay.showTripAnalysis for $packageName (isProfitable=${tripInfo.isProfitable})")
                tripOverlay.showTripAnalysis(tripInfo)
            } else {
                Log.d(TAG, "No valid trip info found in $packageName, hiding overlay")
                tripOverlay.hide()
            }
        } else {
            // Only hide overlay when switching away from ride apps
            if (lastPackageName != packageName) {
                tripOverlay.hide()
            }
        }
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
            "com.didiglobal.driver"
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