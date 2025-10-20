package com.rideanalyzer.app.util

import android.util.Log
import com.rideanalyzer.app.model.TripInfo
import java.util.regex.Pattern

/**
 * Utility class specifically designed to extract trip information from Uber and DiDi apps.
 * This class focuses on the specific UI patterns and text formats used by these ride-sharing apps.
 */
class RideAppTextExtractor {
    
    /**
     * Extracts trip information from text specifically from Uber or DiDi apps.
     * This method is optimized for the specific text patterns used by these apps.
     */
    fun extractTripInfoFromRideApp(text: String, packageName: String): TripInfo? {
        Log.d(TAG, "Extracting trip info from ride app text. Package: $packageName")
        
        val tripInfo = TripInfo()
        
        // Determine platform based on package name
        tripInfo.platform = when {
            packageName.startsWith("com.ubercab") -> "Uber"
            packageName.startsWith("com.didi") -> "DiDi"
            else -> "Unknown Ride App"
        }
        
        Log.d(TAG, "Detected platform: ${tripInfo.platform}")
        
        // Extract information based on the platform
        when (tripInfo.platform) {
            "Uber" -> extractUberInfo(text, tripInfo)
            "DiDi" -> extractDiDiInfo(text, tripInfo)
            else -> extractGenericRideInfo(text, tripInfo)
        }
        
        // Validate and return
        return if (tripInfo.isValid()) {
            Log.d(TAG, "Successfully extracted valid trip info: $tripInfo")
            tripInfo
        } else {
            Log.d(TAG, "Failed to extract valid trip info. Platform: ${tripInfo.platform}, Price: ${tripInfo.price}, Distance: ${tripInfo.distance}")
            null
        }
    }
    
    /**
     * Extract trip information specifically from Uber app text.
     * Uber typically uses formats like:
     * - Price: "ARS10,200" or "$15.50"
     * - Distance: "2.4 km" or "1.5 mi"
     * - Time: "43 min"
     */
    private fun extractUberInfo(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting Uber-specific information")
        
        // Extract price with Uber-specific patterns
        extractPrice(text, tripInfo)
        
        // Extract distance and time for both pickup and trip
        extractDistanceAndTime(text, tripInfo)
        
        // Extract rating if available
        extractRating(text, tripInfo)
    }
    
    /**
     * Extract trip information specifically from DiDi app text.
     * DiDi may use different formats or additional information.
     */
    private fun extractDiDiInfo(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting DiDi-specific information")
        
        // Extract price with DiDi-specific patterns
        extractPrice(text, tripInfo)
        
        // Extract distance and time for both pickup and trip
        extractDistanceAndTime(text, tripInfo)
        
        // Extract rating if available
        extractRating(text, tripInfo)
    }
    
    /**
     * Extract generic ride app information when platform is not clearly identified.
     */
    private fun extractGenericRideInfo(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting generic ride app information")
        
        // Use generic patterns for all ride apps
        extractPrice(text, tripInfo)
        extractDistanceAndTime(text, tripInfo)
        extractRating(text, tripInfo)
    }
    
    /**
     * Extract price information from text using comprehensive patterns.
     */
    private fun extractPrice(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting price information")
        
        // Pattern for currency followed by amount (e.g., "ARS 10,200", "$15.50")
        val pricePatterns = listOf(
            Regex("(ARS|\\$|€|£)\\s*([\\d,.]+)", RegexOption.IGNORE_CASE),
            Regex("([\\d,.]+)\\s*(ARS|\\$|€|£)", RegexOption.IGNORE_CASE)
        )
        
        var maxPrice = 0.0
        var foundPrice = false
        
        for (pattern in pricePatterns) {
            val matches = pattern.findAll(text)
            matches.forEach { matchResult ->
                val groups = matchResult.groupValues
                val currency = if (groups[1].isCurrencySymbol()) groups[1] else groups[2]
                val amount = if (groups[1].isCurrencySymbol()) groups[2] else groups[1]
                
                val priceValue = amount.replace(",", "").replace(".", "").toDoubleOrNull()
                if (priceValue != null && priceValue > maxPrice && priceValue > 100) { // Minimum reasonable price
                    maxPrice = priceValue
                    tripInfo.currency = currency
                    foundPrice = true
                    Log.d(TAG, "Found price: $priceValue $currency from '$matchResult'")
                }
            }
        }
        
        if (foundPrice) {
            tripInfo.price = maxPrice
            Log.d(TAG, "Final extracted price: ${tripInfo.price} ${tripInfo.currency}")
        } else {
            Log.d(TAG, "No valid price found")
        }
    }
    
    /**
     * Extract distance and time information, summing pickup and trip values.
     */
    private fun extractDistanceAndTime(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting distance and time information")
        
        var totalDistance = 0.0
        var totalMinutes = 0
        
        // Look for "A X min (Y km)" for pickup
        val pickupPattern = Regex("A (\\d+)\\s*min.*\\((\\d+[,.]\\d+)\\s*km\\)", RegexOption.IGNORE_CASE)
        val pickupMatches = pickupPattern.findAll(text)
        var pickupCount = 0
        pickupMatches.forEach { matchResult ->
            pickupCount++
            val minutes = matchResult.groupValues[1].toIntOrNull() ?: 0
            val distance = matchResult.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 0.0
            totalMinutes += minutes
            totalDistance += distance
            Log.d(TAG, "Pickup match #$pickupCount: ${minutes}min, ${distance}km (${matchResult.value})")
        }
        
        if (pickupCount > 0) {
            Log.d(TAG, "Found $pickupCount pickup matches, total: ${totalMinutes}min, ${totalDistance}km")
        }
        
        // Look for "Viaje: X min (Y km)" for the main trip
        val tripPattern = Regex("Viaje:\\s*(\\d+)\\s*min.*\\((\\d+[,.]\\d+)\\s*km\\)", RegexOption.IGNORE_CASE)
        val tripMatches = tripPattern.findAll(text)
        var tripCount = 0
        tripMatches.forEach { matchResult ->
            tripCount++
            val minutes = matchResult.groupValues[1].toIntOrNull() ?: 0
            val distance = matchResult.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 0.0
            totalMinutes += minutes
            totalDistance += distance
            Log.d(TAG, "Trip match #$tripCount: ${minutes}min, ${distance}km (${matchResult.value})")
        }
        
        if (tripCount > 0) {
            Log.d(TAG, "Found $tripCount trip matches, total: ${totalMinutes}min, ${totalDistance}km")
        }
        
        // If we didn't find the specific pickup/trip patterns, try generic patterns
        if (totalDistance == 0.0 && totalMinutes == 0) {
            Log.d(TAG, "No specific pickup/trip patterns found, trying generic patterns")
            
            // Extract distance with generic patterns
            val distancePattern = Regex("(\\d+(?:[.,]\\d+)?)\\s*(km|mi)", RegexOption.IGNORE_CASE)
            val distanceMatcher = distancePattern.find(text)
            distanceMatcher?.let { matchResult ->
                val distanceValue = matchResult.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (distanceValue != null && distanceValue > 0) {
                    totalDistance = distanceValue
                    tripInfo.distanceUnit = matchResult.groupValues[2].lowercase()
                    Log.d(TAG, "Extracted generic distance: ${totalDistance} ${tripInfo.distanceUnit}")
                }
            }
            
            // Extract time with generic patterns
            val timePatterns = listOf(
                Regex("(\\d+)\\s*(min|minutes)", RegexOption.IGNORE_CASE),
                Regex("(\\d+)\\s*(min|m)", RegexOption.IGNORE_CASE)
            )
            
            var timeFound = false
            for (pattern in timePatterns) {
                if (timeFound) break
                val matchResult = pattern.find(text)
                matchResult?.let {
                    val timeValue = it.groupValues[1].toIntOrNull()
                    if (timeValue != null && timeValue > 0) {
                        totalMinutes = timeValue
                        Log.d(TAG, "Extracted generic time: ${totalMinutes} min")
                        timeFound = true
                    }
                }
            }
        }
        
        tripInfo.distance = totalDistance
        tripInfo.estimatedMinutes = totalMinutes
        tripInfo.distanceUnit = "km" // Default to km
        
        Log.d(TAG, "Final extracted data - Distance: ${tripInfo.distance} km, Time: ${tripInfo.estimatedMinutes} min")
    }
    
    /**
     * Extract rating information from text.
     */
    private fun extractRating(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting rating information")
        
        val ratingMatcher = Regex("([\\d.,]+)\\s*\\(\\d+\\s*(ratings?|reviews?)\\)", RegexOption.IGNORE_CASE).find(text)
        ratingMatcher?.let { matchResult ->
            val ratingValue = matchResult.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (ratingValue != null) {
                tripInfo.rating = ratingValue
                Log.d(TAG, "Extracted rating: ${tripInfo.rating}")
            }
        }
    }
    
    private fun String.isCurrencySymbol(): Boolean {
        return this in listOf("$", "€", "£", "ARS")
    }
    
    companion object {
        private const val TAG = "RideAppTextExtractor"
    }
}