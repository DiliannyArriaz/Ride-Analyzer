package com.rideanalyzer.app.util

import android.util.Log
import com.rideanalyzer.app.model.TripInfo

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
        Log.d(TAG, "=== EXTRACTING TRIP INFO FROM RIDE APP ===")
        Log.d(TAG, "Package name: $packageName")
        Log.d(TAG, "Text length: ${text.length}")
        
        val tripInfo = TripInfo()
        
        // Determine platform based on package name
        tripInfo.platform = when {
            packageName.startsWith("com.ubercab") -> "Uber"
            packageName.startsWith("com.didiglobal.driver") -> "DiDi"
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
        
        // Extract distance with Uber-specific patterns (sum pickup and trip distances)
        extractTotalDistance(text, tripInfo)
        
        // Extract time with Uber-specific patterns (sum pickup and trip times)
        extractTotalTime(text, tripInfo)
        
        // Extract rating if available
        extractRating(text, tripInfo)
    }
    
    /**
     * Extract trip information specifically from DiDi app text.
     * DiDi may use different formats or additional information.
     */
    private fun extractDiDiInfo(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting DiDi-specific information")
        Log.d(TAG, "DiDi text analysis - length: ${text.length}")
        
        // Extract price with DiDi-specific patterns
        extractPrice(text, tripInfo)
        
        // Extract distance with DiDi-specific patterns (sum pickup and trip distances)
        extractTotalDistance(text, tripInfo)
        
        // Extract time with DiDi-specific patterns (sum pickup and trip times)
        extractTotalTime(text, tripInfo)
        
        // Extract rating if available
        extractRating(text, tripInfo)
        
        Log.d(TAG, "DiDi extraction completed - TripInfo: $tripInfo")
    }
    
    /**
     * Extract generic ride app information when platform is not clearly identified.
     */
    private fun extractGenericRideInfo(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting generic ride app information")
        
        // Use generic patterns for all ride apps
        extractPrice(text, tripInfo)
        extractTotalDistance(text, tripInfo)
        extractTotalTime(text, tripInfo)
        extractRating(text, tripInfo)
    }
    
    /**
     * Extract price information from text using comprehensive patterns.
     */
    private fun extractPrice(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting price information")
        Log.d(TAG, "Price extraction - text length: ${text.length}")
        
        // Pattern for currency followed by amount (e.g., "ARS10,200", "$15.50")
        // Updated to avoid detecting prices with + sign like "+ARS 9435"
        val pricePatterns = listOf(
            Regex("(?<!\\+)\\s*(ARS|\\$|€|£)\\s*([\\d,.]+)", RegexOption.IGNORE_CASE),
            Regex("([\\d,.]+)\\s*(ARS|\\$|€|£)(?!\\+)", RegexOption.IGNORE_CASE)
        )
        
        var maxPrice = 0.0
        var foundPrice = false
        
        for (pattern in pricePatterns) {
            val matches = pattern.findAll(text)
            matches.forEach { match ->
                val groups = match.groupValues
                val currency = if (groups[1].isCurrencySymbol()) groups[1] else groups[2]
                val amount = if (groups[1].isCurrencySymbol()) groups[2] else groups[1]
                
                // Properly parse the amount with thousands separators and decimal points
                val cleanedAmount = amount.replace(",", "").replace(".", "")
                val priceValue = cleanedAmount.toDoubleOrNull()
                Log.d(TAG, "Price pattern match - currency: $currency, amount: $amount, cleaned: $cleanedAmount, parsed value: $priceValue")
                
                // Add reasonable limits to avoid picking up random numbers
                if (priceValue != null && priceValue > maxPrice && priceValue > 100 && priceValue < 100000) { // Reasonable price range for rides
                    maxPrice = priceValue
                    tripInfo.currency = currency
                    foundPrice = true
                    Log.d(TAG, "Found price candidate: $priceValue $currency from '$match'")
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
     * Extract and sum both pickup and trip distances.
     * Looks for patterns like "A 6 min (2.0 km)" and "Viaje: 20 min (8.7 km)"
     */
    private fun extractTotalDistance(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting total distance information")
        Log.d(TAG, "Distance extraction - text length: ${text.length}")
        
        var totalDistance = 0.0
        
        // Look for pickup distance pattern: "A X min (Y km)"
        val pickupPattern = Regex("A\\s+\\d+\\s*min.*?\\((\\d+(?:[.,]\\d+)?)\\s*(km|mi)\\)", RegexOption.IGNORE_CASE)
        val pickupMatches = pickupPattern.findAll(text)
        pickupMatches.forEach { match ->
            val distanceValue = match.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (distanceValue != null) {
                totalDistance += distanceValue
                Log.d(TAG, "Found pickup distance: $distanceValue ${match.groupValues[2]} from match: ${match.value}")
            }
        }
        
        // Look for trip distance pattern: "Viaje: X min (Y km)"
        val tripPattern = Regex("Viaje:\\s*\\d+\\s*min.*?\\((\\d+(?:[.,]\\d+)?)\\s*(km|mi)\\)", RegexOption.IGNORE_CASE)
        val tripMatches = tripPattern.findAll(text)
        tripMatches.forEach { match ->
            val distanceValue = match.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (distanceValue != null) {
                totalDistance += distanceValue
                Log.d(TAG, "Found trip distance: $distanceValue ${match.groupValues[2]} from match: ${match.value}")
            }
        }
        
        // If we didn't find the specific patterns, fall back to generic distance extraction
        if (totalDistance == 0.0) {
            Log.d(TAG, "No specific pickup/trip distances found, using generic distance extraction")
            val genericDistanceMatcher = Regex("(\\d+(?:[.,]\\d+)?)\\s*(km|mi)", RegexOption.IGNORE_CASE).find(text)
            genericDistanceMatcher?.let {
                val distanceValue = it.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (distanceValue != null && distanceValue > 0) {
                    totalDistance = distanceValue
                    Log.d(TAG, "Found generic distance: $distanceValue ${it.groupValues[2]} from match: ${it.value}")
                }
            }
        }
        
        if (totalDistance > 0) {
            tripInfo.distance = totalDistance
            // Always set to km for consistency
            tripInfo.distanceUnit = "km"
            Log.d(TAG, "Final total distance: ${tripInfo.distance} ${tripInfo.distanceUnit}")
        } else {
            Log.d(TAG, "No distance found")
        }
    }
    
    /**
     * Extract and sum both pickup and trip times.
     * Looks for patterns like "A 6 min (2.0 km)" and "Viaje: 20 min (8.7 km)"
     */
    private fun extractTotalTime(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting total time information")
        Log.d(TAG, "Time extraction - text length: ${text.length}")
        
        var totalTime = 0
        
        // Look for pickup time pattern: "A X min (Y km)"
        val pickupPattern = Regex("A\\s+(\\d+)\\s*min", RegexOption.IGNORE_CASE)
        val pickupMatches = pickupPattern.findAll(text)
        pickupMatches.forEach { match ->
            val timeValue = match.groupValues[1].toIntOrNull()
            if (timeValue != null) {
                totalTime += timeValue
                Log.d(TAG, "Found pickup time: $timeValue min from match: ${match.value}")
            }
        }
        
        // Look for trip time pattern: "Viaje: X min (Y km)"
        val tripPattern = Regex("Viaje:\\s*(\\d+)\\s*min", RegexOption.IGNORE_CASE)
        val tripMatches = tripPattern.findAll(text)
        tripMatches.forEach { match ->
            val timeValue = match.groupValues[1].toIntOrNull()
            if (timeValue != null) {
                totalTime += timeValue
                Log.d(TAG, "Found trip time: $timeValue min from match: ${match.value}")
            }
        }
        
        // If we didn't find the specific patterns, fall back to generic time extraction
        if (totalTime == 0) {
            Log.d(TAG, "No specific pickup/trip times found, using generic time extraction")
            val genericTimeMatcher = Regex("(\\d+)\\s*(min|minutes)", RegexOption.IGNORE_CASE).find(text)
            genericTimeMatcher?.let {
                val timeValue = it.groupValues[1].toIntOrNull()
                if (timeValue != null && timeValue > 0) {
                    totalTime = timeValue
                    Log.d(TAG, "Found generic time: $timeValue min from match: ${it.value}")
                }
            }
        }
        
        if (totalTime > 0) {
            tripInfo.estimatedMinutes = totalTime
            Log.d(TAG, "Final total time: ${tripInfo.estimatedMinutes} min")
        } else {
            Log.d(TAG, "No time found")
        }
    }
    
    /**
     * Extract rating information from text.
     */
    private fun extractRating(text: String, tripInfo: TripInfo) {
        Log.d(TAG, "Extracting rating information")
        Log.d(TAG, "Rating extraction - text length: ${text.length}")
        
        val ratingMatcher = Regex("([\\d.,]+)\\s*\\(\\d+\\s*(ratings?|reviews?)\\)", RegexOption.IGNORE_CASE).find(text)
        ratingMatcher?.let {
            val ratingValue = it.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (ratingValue != null) {
                tripInfo.rating = ratingValue
                Log.d(TAG, "Extracted rating: ${tripInfo.rating} from match: ${it.value}")
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