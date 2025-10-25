package com.rideanalyzer.app.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.rideanalyzer.app.ml.TextRecognizer
import com.rideanalyzer.app.model.TripInfo
import com.rideanalyzer.app.ui.TripOverlay
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TripAnalyzer(context: Context) {

    private val textRecognizer = TextRecognizer()
    private val tripOverlay = TripOverlay(context)
    private val context = context // Store context for permission checking
    private var showAllTextForTesting = false // Flag to control whether to show all detected text
    private var lastAnalysisTime: Long = 0
    private val MIN_ANALYSIS_INTERVAL = 500L // Reduced to 500ms for faster updates with multiple trips
    
    // Configuration parameters (kept for API compatibility but using original logic)
    var desiredHourlyRate: Double = 10000.0 // ARS per hour
    var minRating: Double? = null // Optional minimum rating

    // Method to enable showing all detected text for testing
    fun enableShowAllTextForTesting(enabled: Boolean) {
        showAllTextForTesting = enabled
        Log.d(TAG, "Show all text for testing: $enabled")
    }

    // Method to check if we have overlay permission
    private fun hasOverlayPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Overlay permission not needed before Android M
        }
        Log.d(TAG, "Overlay permission check: $hasPermission")
        return hasPermission
    }

    /**
     * Analiza una captura de pantalla usando callbacks.
     */
    fun analyzeScreenshot(screenshot: Bitmap, callback: (TripInfo?) -> Unit) {
        // Debounce mechanism to prevent excessive processing on low-end devices
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < MIN_ANALYSIS_INTERVAL) {
            Log.d(TAG, "Skipping analysis - debounce active (${currentTime - lastAnalysisTime}ms since last analysis)")
            callback(null)
            return
        }
        lastAnalysisTime = currentTime
        
        Log.d(TAG, "=== ANALYZING SCREENSHOT (Callback) ===")
        Log.d(TAG, "Screenshot dimensions: ${screenshot.width}x${screenshot.height}")
        
        // Check overlay permission
        if (!hasOverlayPermission()) {
            Log.w(TAG, "Overlay permission not granted - overlay may not appear")
        }
        
        // Check if screenshot is valid
        if (screenshot.width <= 0 || screenshot.height <= 0) {
            Log.e(TAG, "Invalid screenshot dimensions")
            callback(null)
            return
        }
        
        // Check if screenshot is mostly black/empty
        val isEmpty = isBitmapEmpty(screenshot)
        if (isEmpty) {
            Log.d(TAG, "Screenshot appears to be empty/black")
            callback(null)
            return
        }
        
        // Convertir el Bitmap a InputImage
        val image = InputImage.fromBitmap(screenshot, 0)
        
        // Process text recognition in a coroutine
        CoroutineScope(Dispatchers.Main).launch {
            // Perform heavy operations on IO thread
            val recognizedText = withContext(Dispatchers.IO) {
                textRecognizer.recognizeText(image)
            }
            
            Log.d(TAG, "Text recognition successful")
            Log.d(TAG, "Recognized text length: ${recognizedText.length}")
            
            // Log first 2000 characters of recognized text for debugging (increased from 1000)
            val logText = if (recognizedText.length > 2000) recognizedText.substring(0, 2000) + "..." else recognizedText
            Log.d(TAG, "Recognized text: $logText")
            
            // If we're in testing mode, show all detected text in the overlay
            if (showAllTextForTesting) {
                Log.d(TAG, "Showing all detected text in overlay for testing")
                if (hasOverlayPermission()) {
                    tripOverlay.showAllDetectedText(recognizedText)
                } else {
                    Log.w(TAG, "Cannot show overlay - no permission")
                }
            }
            
            if (recognizedText.isBlank()) {
                Log.d(TAG, "No text recognized in screenshot")
                // Log some image statistics for debugging
                logImageStats(screenshot)
                if (!screenshot.isRecycled) {
                    screenshot.recycle()
                }
                callback(null)
                return@launch
            }
            
            // Even if text is minimal, still process it
            try {
                // Process recognized text on IO thread
                val tripInfo = withContext(Dispatchers.IO) {
                    processRecognizedText(recognizedText)
                }
                
                if (tripInfo.isValid()) {
                    tripInfo.isProfitable = calculateProfitability(tripInfo)
                    Log.d(TAG, "=== SHOWING TRIP OVERLAY ===")
                    Log.d(TAG, "Final trip info: $tripInfo")
                    if (hasOverlayPermission()) {
                        tripOverlay.showTripAnalysis(tripInfo)
                    } else {
                        Log.w(TAG, "Cannot show overlay - no permission")
                    }
                    callback(tripInfo)
                } else {
                    Log.d(TAG, "Trip info is null or invalid, not showing overlay")
                    Log.d(TAG, "Trip info validity check - Platform: ${tripInfo.platform}, Price: ${tripInfo.price}, Distance: ${tripInfo.distance}")
                    
                    // Try alternative processing for specific patterns
                    val alternativeTripInfo = processAlternativePatterns(recognizedText)
                    if (alternativeTripInfo.isValid()) {
                        alternativeTripInfo.isProfitable = calculateProfitability(alternativeTripInfo)
                        Log.d(TAG, "=== SHOWING TRIP OVERLAY (Alternative) ===")
                        Log.d(TAG, "Alternative trip info: $alternativeTripInfo")
                        if (hasOverlayPermission()) {
                            tripOverlay.showTripAnalysis(alternativeTripInfo)
                        } else {
                            Log.w(TAG, "Cannot show overlay - no permission")
                        }
                        callback(alternativeTripInfo)
                    } else {
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing recognized text", e)
                callback(null)
            } finally {
                if (!screenshot.isRecycled) {
                    screenshot.recycle()
                }
            }
        }
    }

    /**
     * Alternative processing for specific patterns
     */
    private fun processAlternativePatterns(text: String): TripInfo {
        Log.d(TAG, "=== PROCESSING ALTERNATIVE PATTERNS ===")
        val tripInfo = TripInfo()
        
        // Look for Uber-specific patterns that might be missed by the main processor
        // Pattern: ARS10,200 or ARS 10,200 (without + symbol)
        val pricePattern = Regex("ARS\\s*(\\d+(?:[\\.,]\\d{3})*(?:[\\.,]\\d{2})?)")
        val priceMatch = pricePattern.find(text)
        priceMatch?.let {
            val cleanAmount = it.groupValues[1].replace(".", "").replace(",", ".")
            val priceValue = cleanAmount.toDoubleOrNull()
            if (priceValue != null && priceValue > 100) {
                tripInfo.price = priceValue
                tripInfo.currency = "ARS"
                tripInfo.platform = "Uber"
                Log.d(TAG, "Found Uber alternative price: $priceValue ARS")
            }
        }
        
        // Look for distance patterns: 1,2km or 18,7km
        val distancePattern = Regex("(\\d+(?:[\\.,]\\d+)?)\\s*km")
        val distanceMatch = distancePattern.find(text)
        distanceMatch?.let {
            val distanceValue = it.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (distanceValue != null && distanceValue > 0) {
                tripInfo.distance = distanceValue
                tripInfo.distanceUnit = "km"
                if (tripInfo.platform.isNullOrBlank()) {
                    tripInfo.platform = "Uber" // Default to Uber if no platform detected
                }
                Log.d(TAG, "Found alternative distance: $distanceValue km")
            }
        }
        
        // Look for time patterns: 4min or 39min
        val timePattern = Regex("(\\d+)\\s*min")
        val timeMatch = timePattern.find(text)
        timeMatch?.let {
            val timeValue = it.groupValues[1].toIntOrNull()
            if (timeValue != null && timeValue > 0) {
                tripInfo.estimatedMinutes = timeValue
                if (tripInfo.platform.isNullOrBlank()) {
                    tripInfo.platform = "Uber" // Default to Uber if no platform detected
                }
                Log.d(TAG, "Found alternative time: $timeValue min")
            }
        }
        
        Log.d(TAG, "Alternative processing result: $tripInfo")
        return tripInfo
    }

    private fun isBitmapEmpty(bitmap: Bitmap): Boolean {
        // Check if bitmap is mostly black/empty
        var totalPixels = 0
        var blackPixels = 0
        
        // Sample pixels (checking every 5th pixel for better accuracy)
        for (x in 0 until bitmap.width step 5) {
            for (y in 0 until bitmap.height step 5) {
                totalPixels++
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                
                // If pixel is close to black (all RGB values < 20)
                if (red < 20 && green < 20 && blue < 20) {
                    blackPixels++
                }
            }
        }
        
        // If more than 90% of sampled pixels are black, consider it empty
        val isEmpty = totalPixels > 0 && (blackPixels.toDouble() / totalPixels) > 0.90
        Log.d(TAG, "Bitmap empty check - Total pixels sampled: $totalPixels, Black pixels: $blackPixels, Is empty: $isEmpty")
        return isEmpty
    }
    
    private fun logImageStats(bitmap: Bitmap) {
        Log.d(TAG, "Image stats - Width: ${bitmap.width}, Height: ${bitmap.height}, Config: ${bitmap.config}")
        
        // Sample more pixel values for debugging
        if (bitmap.width > 0 && bitmap.height > 0) {
            val samplePoints = listOf(
                Pair(0, 0),
                Pair(bitmap.width / 4, bitmap.height / 4),
                Pair(bitmap.width / 2, bitmap.height / 2),
                Pair(3 * bitmap.width / 4, 3 * bitmap.height / 4),
                Pair(bitmap.width - 1, bitmap.height - 1)
            )
            
            samplePoints.forEachIndexed { index, point ->
                if (point.first < bitmap.width && point.second < bitmap.height) {
                    val pixel = bitmap.getPixel(point.first, point.second)
                    val red = android.graphics.Color.red(pixel)
                    val green = android.graphics.Color.green(pixel)
                    val blue = android.graphics.Color.blue(pixel)
                    Log.d(TAG, "Sample pixel $index at (${point.first}, ${point.second}): RGB($red, $green, $blue)")
                }
            }
        }
    }

    /**
     * Process trip information extracted from AccessibilityService
     */
    fun processAccessibilityData(price: String, distance: String, time: String, rating: String? = null): TripInfo {
        // Debounce mechanism to prevent excessive processing on low-end devices
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < MIN_ANALYSIS_INTERVAL) {
            Log.d(TAG, "Skipping accessibility data processing - debounce active (${currentTime - lastAnalysisTime}ms since last analysis)")
            return TripInfo()
        }
        lastAnalysisTime = currentTime
        val tripInfo = TripInfo()
        
        // Parse price (e.g., "ARS10,200")
        val priceMatcher = PRICE_PATTERN.matcher(price)
        if (priceMatcher.find()) {
            val priceString = priceMatcher.group(2)?.replace(".", "")?.replace(',', '.') ?: "0.0"
            val parsedPrice = priceString.toDoubleOrNull() ?: 0.0
            tripInfo.currency = priceMatcher.group(1) ?: "ARS"
            tripInfo.price = parsedPrice
            Log.d(TAG, "Parsed price: ${tripInfo.price} ${tripInfo.currency} from '$price'")
        }
        
        // Parse distance (e.g., "24.0 km")
        val distanceMatcher = DISTANCE_PATTERN.matcher(distance)
        if (distanceMatcher.find()) {
            val distanceValue = distanceMatcher.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            tripInfo.distance = distanceValue
            tripInfo.distanceUnit = distanceMatcher.group(2)?.lowercase() ?: "km"
            Log.d(TAG, "Parsed distance: ${tripInfo.distance} ${tripInfo.distanceUnit} from '$distance'")
        }
        
        // Parse time (e.g., "43 min")
        val timeMatcher = TIME_PATTERN.matcher(time)
        if (timeMatcher.find()) {
            val timeValue = timeMatcher.group(1)?.toIntOrNull() ?: 0
            tripInfo.estimatedMinutes = timeValue
            Log.d(TAG, "Parsed time: ${tripInfo.estimatedMinutes} min from '$time'")
        }
        
        // Parse rating if available
        rating?.let {
            val ratingMatcher = RATING_PATTERN.matcher(it)
            if (ratingMatcher.find()) {
                val ratingValue = ratingMatcher.group(1)?.replace(',', '.')?.toDoubleOrNull()
                tripInfo.rating = ratingValue
                Log.d(TAG, "Parsed rating: ${tripInfo.rating} from '$rating'")
            }
        }
        
        return tripInfo
    }

    private fun processRecognizedText(text: String): TripInfo {
        Log.d(TAG, "=== PROCESSING RECOGNIZED TEXT ===")
        val lowerText = text.lowercase()
        Log.d(TAG, "Raw text length: ${text.length}")
        Log.d(TAG, "Lowercase text length: ${lowerText.length}")

        // Even if it doesn't look like a trip screen, still try to extract trip info
        // This is because OCR might not capture all the keywords
        val tripInfo = extractTripInfo(text, lowerText)
        Log.d(TAG, "Extracted trip info: $tripInfo")

        return tripInfo
    }

    private fun isTripScreen(lowerText: String): Boolean {
        Log.d(TAG, "Checking for trip screen keywords...")
        
        // Log the text we're searching in (first 1000 chars)
        val searchText = if (lowerText.length > 1000) lowerText.substring(0, 1000) + "..." else lowerText
        Log.d(TAG, "Searching in text: $searchText")
        
        // Check each keyword set separately for better debugging
        val uberFound = UBER_KEYWORDS.any { keyword -> 
            val found = lowerText.contains(keyword)
            if (found) Log.d(TAG, "Found Uber keyword: $keyword")
            found
        }
        
        Log.d(TAG, "Keyword search results - Uber: $uberFound")
        
        val allKeywords = UBER_KEYWORDS + TRIP_ACTION_KEYWORDS
        val foundKeyword = allKeywords.any { keyword -> lowerText.contains(keyword) }

        if (foundKeyword) {
            Log.d(TAG, "Trip screen detected based on keywords.")
        } else {
            Log.d(TAG, "No trip keywords found in text.")
        }
        return foundKeyword
    }

    private fun extractTripInfo(originalText: String, lowerText: String): TripInfo {
        val tripInfo = TripInfo()

        // Detect platform (Uber only)
        tripInfo.platform = when {
            UBER_KEYWORDS.any { lowerText.contains(it) } -> "com.ubercab.driver"
            else -> "Desconocido"
        }
        
        Log.d(TAG, "Detected platform: ${tripInfo.platform}")

        // Extract price - handle Uber format only
        val priceMatcher = PRICE_PATTERN.matcher(originalText)
        var maxPrice = 0.0
        var foundPrice = false
        var matchCount = 0
        while (priceMatcher.find()) {
            matchCount++
            // Only Uber format (ARS + numbers)
            val uberPrice = priceMatcher.group(1)?.toDoubleOrNull()
            
            val price = uberPrice ?: 0.0
            Log.d(TAG, "Price match #$matchCount: Uber=$uberPrice -> Final=$price (${priceMatcher.group()})")
            
            // For Uber, specifically exclude prices with + symbol
            val fullMatch = priceMatcher.group()
            if (tripInfo.platform == "com.ubercab.driver" && fullMatch.contains("+")) {
                Log.d(TAG, "Skipping Uber price with + symbol: $fullMatch")
                continue
            }
            
            if (price > maxPrice && price > 100) { // Minimum reasonable price
                maxPrice = price
                tripInfo.currency = "ARS"
                foundPrice = true
                Log.d(TAG, "Selected price candidate: $price ${tripInfo.currency}")
            }
        }
        if (foundPrice) {
            tripInfo.price = maxPrice
            Log.d(TAG, "Final selected price: ${tripInfo.price} ${tripInfo.currency}")
        } else {
            Log.d(TAG, "No valid price found, match count: $matchCount")
            
            // Try alternative price extraction for Uber
            if (tripInfo.platform == "com.ubercab.driver") {
                val alternativePrice = extractAlternativeUberPrice(originalText)
                if (alternativePrice > 0) {
                    tripInfo.price = alternativePrice
                    tripInfo.currency = "ARS"
                    foundPrice = true
                    Log.d(TAG, "Found alternative Uber price: $alternativePrice ARS")
                }
            }
        }

        // Extract distance and time with improved pattern matching
        var totalDistance = 0.0
        var totalMinutes = 0
        
        // First try to find the most comprehensive distance/time pattern
        val comprehensivePattern = Regex("(\\d+)\\s*min\\s*\\(?([^)]*\\d+[,.]\\d+\\s*km|\\d+[,.]\\d+\\s*km[^)]*)\\)?", RegexOption.IGNORE_CASE)
        val comprehensiveMatches = comprehensivePattern.findAll(originalText).toList()
        
        if (comprehensiveMatches.isNotEmpty()) {
            // Take the match with the highest minutes value
            var maxMinutes = 0
            var bestMatch: MatchResult? = null
            
            comprehensiveMatches.forEach { match ->
                val minutes = match.groupValues[1].toIntOrNull() ?: 0
                if (minutes > maxMinutes) {
                    maxMinutes = minutes
                    bestMatch = match
                }
                Log.d(TAG, "Comprehensive pattern match: ${match.value}")
            }
            
            bestMatch?.let { match ->
                totalMinutes = match.groupValues[1].toIntOrNull() ?: 0
                
                // Extract distance from the second group
                val distanceText = match.groupValues[2]
                val distancePattern = Regex("(\\d+[,.]\\d+)\\s*km", RegexOption.IGNORE_CASE)
                val distanceMatch = distancePattern.find(distanceText)
                distanceMatch?.let {
                    totalDistance = it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
                }
                
                Log.d(TAG, "Selected comprehensive match - Time: ${totalMinutes}min, Distance: ${totalDistance}km")
            }
        }
        
        // If comprehensive pattern didn't work, try individual patterns
        if (totalDistance == 0.0 || totalMinutes == 0) {
            Log.d(TAG, "Comprehensive pattern didn't match, trying individual patterns")
            
            // Find maximum distance using generic pattern
            val distanceMatcher = DISTANCE_PATTERN.matcher(originalText)
            var maxDistance = 0.0
            var distanceCount = 0
            while (distanceMatcher.find()) {
                distanceCount++
                val distance = distanceMatcher.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                if (distance > maxDistance) {
                    maxDistance = distance
                    Log.d(TAG, "Generic distance match #$distanceCount: ${distance}km (${distanceMatcher.group()})")
                }
            }
            
            // Find maximum time using generic pattern
            val timeMatcher = TIME_PATTERN.matcher(originalText)
            var maxMinutes = 0
            var timeCount = 0
            while (timeMatcher.find()) {
                timeCount++
                val minutes = timeMatcher.group(1)?.toIntOrNull() ?: 0
                if (minutes > maxMinutes) {
                    maxMinutes = minutes
                    Log.d(TAG, "Generic time match #$timeCount: ${minutes}min (${timeMatcher.group()})")
                }
            }
            
            // Only update if we found better values
            if (maxDistance > totalDistance) {
                totalDistance = maxDistance
            }
            if (maxMinutes > totalMinutes) {
                totalMinutes = maxMinutes
            }
            
            Log.d(TAG, "Generic pattern results - Distance: ${totalDistance}km, Time: ${totalMinutes}min")
        }
        
        // If still no data, try Uber-specific patterns
        if (totalDistance == 0.0 || totalMinutes == 0) {
            Log.d(TAG, "Trying Uber-specific patterns")
            
            // Try pickup pattern
            val pickupMatcher = PICKUP_PATTERN.matcher(originalText)
            var pickupCount = 0
            while (pickupMatcher.find()) {
                pickupCount++
                val minutes = pickupMatcher.group(1)?.toIntOrNull() ?: 0
                val distance = pickupMatcher.group(2)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                if (minutes > totalMinutes) {
                    totalMinutes = minutes
                }
                if (distance > totalDistance) {
                    totalDistance = distance
                }
                Log.d(TAG, "Pickup match #$pickupCount: ${minutes}min, ${distance}km (${pickupMatcher.group()})")
            }
            
            // Try trip pattern
            val tripMatcher = TRIP_PATTERN.matcher(originalText)
            var tripCount = 0
            while (tripMatcher.find()) {
                tripCount++
                val minutes = tripMatcher.group(1)?.toIntOrNull() ?: 0
                val distance = tripMatcher.group(2)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                if (minutes > totalMinutes) {
                    totalMinutes = minutes
                }
                if (distance > totalDistance) {
                    totalDistance = distance
                }
                Log.d(TAG, "Trip match #$tripCount: ${minutes}min, ${distance}km (${tripMatcher.group()})")
            }
        }

        // Update tripInfo with calculated values
        tripInfo.distance = totalDistance
        tripInfo.estimatedMinutes = totalMinutes
        tripInfo.distanceUnit = "km"
        
        Log.d(TAG, "Final extracted data - Distance: ${tripInfo.distance} km, Time: ${tripInfo.estimatedMinutes} min")

        // Extract rating if available
        extractRating(originalText, tripInfo)

        return tripInfo
    }
    
    /**
     * Alternative price extraction for Uber
     */
    private fun extractAlternativeUberPrice(text: String): Double {
        Log.d(TAG, "Extracting alternative Uber price")
        
        // More relaxed Uber price patterns
        val patterns = listOf(
            Regex("ARS\\s*(\\d+(?:[\\.,]\\d{3})*(?:[\\.,]\\d{2})?)"),
            Regex("(\\d+(?:[\\.,]\\d{3})*(?:[\\.,]\\d{2})?)\\s*ARS"),
            Regex("ARS(\\d+(?:[\\.,]\\d{3})*(?:[\\.,]\\d{2})?)")
        )
        
        for (pattern in patterns) {
            val matches = pattern.findAll(text)
            matches.forEach { match ->
                val fullMatch = match.value
                // Skip prices with + symbol
                if (!fullMatch.contains("+")) {
                    val amount = match.groupValues[1]
                    val cleanAmount = amount.replace(".", "").replace(",", ".")
                    val priceValue = cleanAmount.toDoubleOrNull()
                    if (priceValue != null && priceValue > 100) { // Minimum reasonable price
                        Log.d(TAG, "Found alternative Uber price: $priceValue ARS from pattern '$pattern'")
                        return priceValue
                    }
                } else {
                    Log.d(TAG, "Skipping price with + symbol: $fullMatch")
                }
            }
        }
        
        return 0.0
    }

    private fun findGenericValues(text: String): Pair<Double, Int> {
        var maxDistance = 0.0
        var maxMinutes = 0
        
        // Find maximum distance
        val distanceMatcher = DISTANCE_PATTERN.matcher(text)
        var distanceCount = 0
        while (distanceMatcher.find()) {
            distanceCount++
            val distance = distanceMatcher.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            if (distance > maxDistance) {
                maxDistance = distance
                Log.d(TAG, "Generic distance match #$distanceCount: ${distance}km (${distanceMatcher.group()})")
            }
        }
        if (distanceCount > 0) {
            Log.d(TAG, "Found $distanceCount generic distance matches, selected: ${maxDistance}km")
        }
        
        // Find maximum time
        val timeMatcher = TIME_PATTERN.matcher(text)
        var timeCount = 0
        while (timeMatcher.find()) {
            timeCount++
            val minutes = timeMatcher.group(1)?.toIntOrNull() ?: 0
            if (minutes > maxMinutes) {
                maxMinutes = minutes
                Log.d(TAG, "Generic time match #$timeCount: ${minutes}min (${timeMatcher.group()})")
            }
        }
        if (timeCount > 0) {
            Log.d(TAG, "Found $timeCount generic time matches, selected: ${maxMinutes}min")
        }
        
        return Pair(maxDistance, maxMinutes)
    }

    private fun extractRating(text: String, tripInfo: TripInfo) {
        val ratingMatcher = RATING_PATTERN.matcher(text)
        var ratingCount = 0
        while (ratingMatcher.find()) {
            ratingCount++
            val ratingValue = ratingMatcher.group(1)?.replace(',', '.')?.toDoubleOrNull()
            tripInfo.rating = ratingValue
            Log.d(TAG, "Rating match #$ratingCount: $ratingValue (${ratingMatcher.group()})")
        }
        if (ratingCount > 0) {
            Log.d(TAG, "Found $ratingCount rating matches, selected: ${tripInfo.rating}")
        }
    }
    
    fun calculateProfitability(tripInfo: TripInfo): Boolean {
        val (price, distance, minutes) = Triple(tripInfo.price, tripInfo.distance, tripInfo.estimatedMinutes)

        Log.d(TAG, "Calculating profitability - Price: $price, Distance: $distance km, Time: $minutes min")

        if (price <= 0 || distance <= 0) {
            Log.d(TAG, "Invalid values for profitability calculation - Price: $price, Distance: $distance")
            return false
        }

        val pricePerKm = price / distance
        val pricePerMinute = if (minutes > 0) price / minutes else 0.0

        // Update the tripInfo with calculated values
        tripInfo.pricePerKm = pricePerKm
        tripInfo.pricePerMinute = pricePerMinute
        tripInfo.pricePerHour = pricePerMinute * 60

        // Use the configurable desired hourly rate for profitability calculation
        val minPricePerMinute = desiredHourlyRate / 60.0
        // For price per km, we'll use a reasonable threshold based on typical costs
        val minPricePerKm = 50.0 // Minimum price per km (adjustable based on typical costs)
        // Minimum total price for a trip (to avoid considering very small trips)
        val minTotalPrice = 300.0 // Minimum total price for a trip
        
        // Log detailed calculation values
        Log.d(TAG, "Detailed profitability calculation:")
        Log.d(TAG, "  Desired hourly rate: $desiredHourlyRate ARS/hour")
        Log.d(TAG, "  Required price per minute: $minPricePerMinute ARS/minute")
        Log.d(TAG, "  Actual price per minute: $pricePerMinute ARS/minute")
        Log.d(TAG, "  Price per km: $pricePerKm ARS/km")
        Log.d(TAG, "  Minimum price per km threshold: $minPricePerKm ARS/km")
        Log.d(TAG, "  Trip price: $price ARS")
        Log.d(TAG, "  Minimum trip price threshold: $minTotalPrice ARS")
        
        // Primary profitability check: based on desired hourly rate
        val isProfitableByTime = minutes > 0 && pricePerMinute >= minPricePerMinute
        Log.d(TAG, "  Is profitable by time: $isProfitableByTime (minutes > 0 && pricePerMinute >= minPricePerMinute)")
        
        // Secondary checks: basic thresholds to filter out obviously bad trips
        val meetsBasicThresholds = price >= minTotalPrice && pricePerKm >= minPricePerKm
        Log.d(TAG, "  Meets basic thresholds: $meetsBasicThresholds (price >= minTotalPrice && pricePerKm >= minPricePerKm)")
        
        // A trip is profitable if it meets the desired hourly rate AND basic thresholds
        val isProfitable = isProfitableByTime && meetsBasicThresholds
        Log.d(TAG, "  Final profitability result: $isProfitable")

        Log.d(TAG, "Profitability calculation - Price/km: %.2f (threshold: %.2f), Price/min: %.2f (threshold: %.2f), Min price: %.2f (threshold: %.2f), Desired hourly rate: %.2f".format(
            pricePerKm, minPricePerKm, pricePerMinute, minPricePerMinute, price, minTotalPrice, desiredHourlyRate))
        Log.d(TAG, "Is profitable by time: $isProfitableByTime, Meets basic thresholds: $meetsBasicThresholds, Is profitable: $isProfitable")
        return isProfitable
    }

    fun hideOverlay() {
        tripOverlay.onTripNoLongerVisible()
    }

    companion object {
        private const val TAG = "TripAnalyzer"

        // Patrones de Regex (compilados para eficiencia)
        // Soporta formato Uber:
        // 1. Uber: "ARS4518" or "ARS10,200" or "ARS 10,200" (con varios formatos)
        private val PRICE_PATTERN: Pattern = Pattern.compile("(?:ARS\\s*(\\d+(?:[\\.,]\\d{3})*(?:[\\.,]\\d{2})?)|\\$(\\d{1,3}(?:\\.(\\d{3}))*,\\d{2}))")
        
        // Patrones de distancia y tiempo genéricos
        private val DISTANCE_PATTERN: Pattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:km|mi)\\b", Pattern.CASE_INSENSITIVE)
        private val TIME_PATTERN: Pattern = Pattern.compile("(\\d{1,3})\\s*(?:min|m)\\b", Pattern.CASE_INSENSITIVE)
        
        // Rating con formato "5.00 225 viajes"
        private val RATING_PATTERN: Pattern = Pattern.compile("([\\d.,]+)\\s*(\\d+)\\s*viajes?")

        // Patrones específicos para tiempos y distancias
        // Uber: "A 6 min (2.3 km)" y "Viaje: 7 min (3.1 km)"
        private val PICKUP_PATTERN: Pattern = Pattern.compile("(?:A\\s+)?(\\d+)\\s*min\\s*\\((\\d+[,.]\\d+)\\s*km\\)", Pattern.CASE_INSENSITIVE)
        private val TRIP_PATTERN: Pattern = Pattern.compile("(?:Viaje:\\s*)?(\\d+)\\s*min\\s*\\((\\d+[,.]\\d+)\\s*km\\)", Pattern.CASE_INSENSITIVE)

        // Palabras clave
        private val UBER_KEYWORDS = arrayOf("uber", "uberx", "comfort", "black", "suv", "xl")
        private val TRIP_ACTION_KEYWORDS = arrayOf("aceptar", "confirmar", "viaje", "recoger", "solicitar", "pedir", "iniciar", "accept", "confirm", "trip", "pickup", "request")

        // Umbrales de rentabilidad para ARS 10.000/hora (same as original)
        private const val MIN_PRICE_PER_KM = 50.0 // Minimum price per km (adjustable based on typical costs)
        private const val MIN_TOTAL_PRICE = 300.0 // Minimum total price for a trip
    }
}