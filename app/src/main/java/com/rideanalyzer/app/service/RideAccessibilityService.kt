package com.rideanalyzer.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rideanalyzer.app.analyzer.TripAnalyzer
import com.rideanalyzer.app.model.TripInfo
import com.rideanalyzer.app.ui.TripOverlay

class RideAccessibilityService : AccessibilityService() {
    
    private lateinit var tripAnalyzer: TripAnalyzer
    private lateinit var tripOverlay: TripOverlay
    private var isMonitoring = false
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        tripAnalyzer = TripAnalyzer(this)
        tripOverlay = TripOverlay(this)
        isMonitoring = true // Start monitoring automatically when service is connected
        Log.d(TAG, "RideAccessibilityService connected and monitoring started")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isMonitoring) return
        
        event?.let { 
            // Check if we're in Uber or DiDi app or our test app
            val packageName = it.packageName?.toString() ?: return
            val isRideApp = packageName.contains("uber") || packageName.contains("didi") || packageName.contains("rideanalyzer")
            
            if (isRideApp) {
                Log.d(TAG, "Detected ride app or test app: $packageName")
                
                // Diagnostic logging to see what the Accessibility Service is reading
                val root = rootInActiveWindow
                if (root == null) {
                    Log.d(TAG, "Accessibility: rootInActiveWindow es null")
                } else {
                    val sb = StringBuilder()
                    fun dumpNode(node: AccessibilityNodeInfo?, depth: Int = 0) {
                        if (node==null) return
                        sb.append(" ".repeat(depth)).append("text=").append(node.text).append(" class=").append(node.className).append("\n")
                        for (i in 0 until node.childCount) dumpNode(node.getChild(i), depth+2)
                    }
                    dumpNode(root)
                    Log.d(TAG, "Accessibility dump:\n${sb.toString()}")
                }
                
                // Extract trip information from UI hierarchy
                val tripInfo = extractTripInfoFromUI(rootInActiveWindow)
                if (tripInfo != null && tripInfo.isValid()) {
                    Log.d(TAG, "Valid trip info extracted: $tripInfo")
                    tripInfo.isProfitable = tripAnalyzer.calculateProfitability(tripInfo)
                    tripOverlay.showTripAnalysis(tripInfo)
                } else {
                    Log.d(TAG, "No valid trip info found")
                }
            }
        }
    }
    
    private fun extractTripInfoFromUI(nodeInfo: AccessibilityNodeInfo?): TripInfo? {
        nodeInfo?.let {
            val tripInfo = TripInfo()
            var foundPrice = false
            var foundDistance = false
            var foundTime = false
            
            // Traverse the UI hierarchy to find relevant text
            traverseNodes(it) { node ->
                val text = node.text?.toString() ?: node.contentDescription?.toString() ?: return@traverseNodes
                
                Log.d(TAG, "Found node text: $text")
                
                // Try to extract price information
                if (!foundPrice && text.contains(Regex("(ARS|\\$|€|£)\\s*[\\d,.]+"))) {
                    val priceMatcher = Regex("(ARS|\\$|€|£)\\s*([\\d,.]+)").find(text)
                    priceMatcher?.let {
                        val priceValue = it.groupValues[2].replace(",", "").toDoubleOrNull()
                        if (priceValue != null && priceValue > 0) {
                            tripInfo.price = priceValue
                            tripInfo.currency = it.groupValues[1]
                            foundPrice = true
                            Log.d(TAG, "Extracted price: ${tripInfo.price} ${tripInfo.currency}")
                        }
                    }
                }
                
                // Try to extract distance information
                if (!foundDistance && text.contains(Regex("\\d+\\s*(km|mi)"))) {
                    val distanceMatcher = Regex("(\\d+(?:[.,]\\d+)?)\\s*(km|mi)").find(text)
                    distanceMatcher?.let {
                        val distanceValue = it.groupValues[1].replace(",", ".").toDoubleOrNull()
                        if (distanceValue != null && distanceValue > 0) {
                            tripInfo.distance = distanceValue
                            tripInfo.distanceUnit = it.groupValues[2]
                            foundDistance = true
                            Log.d(TAG, "Extracted distance: ${tripInfo.distance} ${tripInfo.distanceUnit}")
                        }
                    }
                }
                
                // Try to extract time information
                if (!foundTime && text.contains(Regex("\\d+\\s*(min|minutes)"))) {
                    val timeMatcher = Regex("(\\d+)\\s*(min|minutes)").find(text)
                    timeMatcher?.let {
                        val timeValue = it.groupValues[1].toIntOrNull()
                        if (timeValue != null && timeValue > 0) {
                            tripInfo.estimatedMinutes = timeValue
                            foundTime = true
                            Log.d(TAG, "Extracted time: ${tripInfo.estimatedMinutes} min")
                        }
                    }
                }
                
                // Try to detect platform
                if (tripInfo.platform == null) {
                    when {
                        text.contains("uber", ignoreCase = true) -> tripInfo.platform = "Uber"
                        text.contains("didi", ignoreCase = true) -> tripInfo.platform = "Didi"
                    }
                }
            }
            
            // If we found at least some information, return the trip info
            if (foundPrice || foundDistance || foundTime) {
                if (tripInfo.platform == null) {
                    tripInfo.platform = "Unknown"
                }
                return tripInfo
            }
        }
        
        return null
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