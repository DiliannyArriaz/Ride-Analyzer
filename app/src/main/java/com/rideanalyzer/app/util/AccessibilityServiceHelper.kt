package com.rideanalyzer.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Helper class for managing and checking the status of accessibility services.
 */
object AccessibilityServiceHelper {
    
    /**
     * Checks if the RideAccessibilityService is currently enabled.
     *
     * @param context The application context
     * @return true if the service is enabled, false otherwise
     */
    fun isRideAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == context.packageName && 
            it.resolveInfo.serviceInfo.name == "com.rideanalyzer.app.service.RideAccessibilityService"
        }
    }
    
    /**
     * Gets a list of all enabled accessibility services.
     *
     * @param context The application context
     * @return List of enabled accessibility service info objects
     */
    fun getEnabledAccessibilityServices(context: Context): List<AccessibilityServiceInfo> {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    }
    
    /**
     * Gets the status message for the RideAccessibilityService.
     *
     * @param context The application context
     * @return A user-friendly status message
     */
    fun getAccessibilityServiceStatusMessage(context: Context): String {
        return if (isRideAccessibilityServiceEnabled(context)) {
            "Accessibility service is running"
        } else {
            "Accessibility service is not enabled"
        }
    }
}