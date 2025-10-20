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

class TripAnalyzer(context: Context) {

    private val textRecognizer = TextRecognizer()
    private val tripOverlay = TripOverlay(context)
    private val context = context // Store context for permission checking
    private var showAllTextForTesting = false // Flag to control whether to show all detected text
    
    // Configuration parameters (kept for API compatibility but using original logic)
    var desiredHourlyRate: Double = 10000.0 // ARS per hour
    var costPerKm: Double = 150.0 // ARS per km
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
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val recognizedText = textRecognizer.recognizeText(image)
                Log.d(TAG, "Text recognition successful")
                Log.d(TAG, "Recognized text length: ${recognizedText.length}")
                
                // Log first 1000 characters of recognized text for debugging
                val logText = if (recognizedText.length > 1000) recognizedText.substring(0, 1000) + "..." else recognizedText
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
                
                try {
                    val tripInfo = processRecognizedText(recognizedText)
                    
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
                        callback(null)
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
    }

    private fun isBitmapEmpty(bitmap: Bitmap): Boolean {
        // Check if bitmap is mostly black/empty
        var totalPixels = 0
        var blackPixels = 0
        
        // Sample pixels (checking every 10th pixel for performance)
        for (x in 0 until bitmap.width step 10) {
            for (y in 0 until bitmap.height step 10) {
                totalPixels++
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                
                // If pixel is close to black (all RGB values < 10)
                if (red < 10 && green < 10 && blue < 10) {
                    blackPixels++
                }
            }
        }
        
        // If more than 95% of sampled pixels are black, consider it empty
        val isEmpty = totalPixels > 0 && (blackPixels.toDouble() / totalPixels) > 0.95
        Log.d(TAG, "Bitmap empty check - Total pixels sampled: $totalPixels, Black pixels: $blackPixels, Is empty: $isEmpty")
        return isEmpty
    }
    
    private fun logImageStats(bitmap: Bitmap) {
        Log.d(TAG, "Image stats - Width: ${bitmap.width}, Height: ${bitmap.height}, Config: ${bitmap.config}")
        
        // Sample a few pixel values for debugging
        if (bitmap.width > 0 && bitmap.height > 0) {
            val samplePoints = listOf(
                Pair(0, 0),
                Pair(bitmap.width / 2, bitmap.height / 2),
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
        val tripInfo = TripInfo()
        
        // Parse price (e.g., "ARS10,200")
        PRICE_PATTERN.matcher(price).let {
            if (it.find()) {
                val priceString = it.group(2)?.replace(".", "")?.replace(',', '.') ?: "0.0"
                val parsedPrice = priceString.toDoubleOrNull() ?: 0.0
                tripInfo.currency = it.group(1) ?: "ARS"
                tripInfo.price = parsedPrice
                Log.d(TAG, "Parsed price: ${tripInfo.price} ${tripInfo.currency} from '$price'")
            }
        }
        
        // Parse distance (e.g., "24.0 km")
        DISTANCE_PATTERN.matcher(distance).let {
            if (it.find()) {
                val distanceValue = it.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                tripInfo.distance = distanceValue
                tripInfo.distanceUnit = it.group(2)?.lowercase() ?: "km"
                Log.d(TAG, "Parsed distance: ${tripInfo.distance} ${tripInfo.distanceUnit} from '$distance'")
            }
        }
        
        // Parse time (e.g., "43 min")
        TIME_PATTERN.matcher(time).let {
            if (it.find()) {
                val timeValue = it.group(1)?.toIntOrNull() ?: 0
                tripInfo.estimatedMinutes = timeValue
                Log.d(TAG, "Parsed time: ${tripInfo.estimatedMinutes} min from '$time'")
            }
        }
        
        // Parse rating if available
        rating?.let {
            RATING_PATTERN.matcher(it).let { matcher ->
                if (matcher.find()) {
                    val ratingValue = matcher.group(1)?.replace(',', '.')?.toDoubleOrNull()
                    tripInfo.rating = ratingValue
                    Log.d(TAG, "Parsed rating: ${tripInfo.rating} from '$rating'")
                }
            }
        }
        
        return tripInfo
    }

    private fun processRecognizedText(text: String): TripInfo {
        Log.d(TAG, "=== PROCESSING RECOGNIZED TEXT ===")
        val lowerText = text.lowercase()
        Log.d(TAG, "Raw text length: ${text.length}")
        Log.d(TAG, "Lowercase text length: ${lowerText.length}")

        val isTrip = isTripScreen(lowerText)
        Log.d(TAG, "Is trip screen: $isTrip")
        
        if (!isTrip) {
            Log.d(TAG, "Not a trip screen, skipping analysis")
            return TripInfo() // Return empty TripInfo
        }

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
        
        val didiFound = DIDI_KEYWORDS.any { keyword -> 
            val found = lowerText.contains(keyword)
            if (found) Log.d(TAG, "Found DiDi keyword: $keyword")
            found
        }
        
        val actionFound = TRIP_ACTION_KEYWORDS.any { keyword -> 
            val found = lowerText.contains(keyword)
            if (found) Log.d(TAG, "Found action keyword: $keyword")
            found
        }
        
        Log.d(TAG, "Keyword search results - Uber: $uberFound, DiDi: $didiFound, Action: $actionFound")
        
        val allKeywords = UBER_KEYWORDS + DIDI_KEYWORDS + TRIP_ACTION_KEYWORDS
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

        // Detect platform
        tripInfo.platform = when {
            UBER_KEYWORDS.any { lowerText.contains(it) } -> "Uber"
            DIDI_KEYWORDS.any { lowerText.contains(it) } -> "Didi"
            else -> "Desconocido"
        }
        
        Log.d(TAG, "Detected platform: ${tripInfo.platform}")

        // Extract price - more comprehensive pattern matching
        PRICE_PATTERN.matcher(originalText).let {
            var maxPrice = 0.0
            var foundPrice = false
            var matchCount = 0
            while (it.find()) {
                matchCount++
                val priceString = it.group(2)?.replace(".", "")?.replace(',', '.') ?: "0.0"
                val price = priceString.toDoubleOrNull() ?: 0.0
                Log.d(TAG, "Price match #$matchCount: '$priceString' -> $price (${it.group()})")
                // El precio del viaje suele ser el número más grande en la pantalla.
                if (price > maxPrice && price > 100) { // Minimum reasonable price
                    maxPrice = price
                    tripInfo.currency = it.group(1) ?: "ARS"
                    foundPrice = true
                    Log.d(TAG, "Selected price candidate: $price ${tripInfo.currency}")
                }
            }
            if (foundPrice) {
                tripInfo.price = maxPrice
                Log.d(TAG, "Final selected price: ${tripInfo.price} ${tripInfo.currency}")
            } else {
                Log.d(TAG, "No valid price found, match count: $matchCount")
            }
        }

        // Extract distance and time for both pickup and trip
        var totalDistance = 0.0
        var totalMinutes = 0

        // Busca "A X min (Y km)" para la recogida
        PICKUP_PATTERN.matcher(originalText).let {
            var pickupCount = 0
            while (it.find()) {
                pickupCount++
                val minutes = it.group(1)?.toIntOrNull() ?: 0
                val distance = it.group(2)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                totalMinutes += minutes
                totalDistance += distance
                Log.d(TAG, "Pickup match #$pickupCount: ${minutes}min, ${distance}km (${it.group()})")
            }
            if (pickupCount > 0) {
                Log.d(TAG, "Found $pickupCount pickup matches, total: ${totalMinutes}min, ${totalDistance}km")
            }
        }

        // Busca "Viaje: X min (Y km)" para el viaje principal
        TRIP_PATTERN.matcher(originalText).let {
            var tripCount = 0
            while (it.find()) {
                tripCount++
                val minutes = it.group(1)?.toIntOrNull() ?: 0
                val distance = it.group(2)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                totalMinutes += minutes
                totalDistance += distance
                Log.d(TAG, "Trip match #$tripCount: ${minutes}min, ${distance}km (${it.group()})")
            }
            if (tripCount > 0) {
                Log.d(TAG, "Found $tripCount trip matches, total: ${totalMinutes}min, ${totalDistance}km")
            }
        }

        // Si no se encontraron los patrones de viaje/recogida, intenta con los patrones genéricos
        if (totalDistance == 0.0 && totalMinutes == 0) {
            DISTANCE_PATTERN.matcher(originalText).let { 
                var distanceCount = 0
                while (it.find()) {
                    distanceCount++
                    val distance = it.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                    if (distance > totalDistance) { // Take the largest distance
                        totalDistance = distance
                        Log.d(TAG, "Generic distance match #$distanceCount: ${distance}km (${it.group()})")
                    }
                }
                if (distanceCount > 0) {
                    Log.d(TAG, "Found $distanceCount generic distance matches, selected: ${totalDistance}km")
                }
            }
            
            TIME_PATTERN.matcher(originalText).let { 
                var timeCount = 0
                while (it.find()) {
                    timeCount++
                    val minutes = it.group(1)?.toIntOrNull() ?: 0
                    if (minutes > totalMinutes) { // Take the largest time
                        totalMinutes = minutes
                        Log.d(TAG, "Generic time match #$timeCount: ${minutes}min (${it.group()})")
                    }
                }
                if (timeCount > 0) {
                    Log.d(TAG, "Found $timeCount generic time matches, selected: ${totalMinutes}min")
                }
            }
        }

        tripInfo.distance = totalDistance
        tripInfo.estimatedMinutes = totalMinutes
        tripInfo.distanceUnit = "km"
        
        Log.d(TAG, "Final extracted data - Distance: ${tripInfo.distance} km, Time: ${tripInfo.estimatedMinutes} min")

        // Extract rating if available
        RATING_PATTERN.matcher(originalText).let {
            var ratingCount = 0
            while (it.find()) {
                ratingCount++
                val ratingValue = it.group(1)?.replace(',', '.')?.toDoubleOrNull()
                tripInfo.rating = ratingValue
                Log.d(TAG, "Rating match #$ratingCount: $ratingValue (${it.group()})")
            }
            if (ratingCount > 0) {
                Log.d(TAG, "Found $ratingCount rating matches, selected: ${tripInfo.rating}")
            }
        }

        return tripInfo
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

        tripInfo.pricePerKm = pricePerKm
        tripInfo.pricePerMinute = pricePerMinute

        // Use the same profitability logic as the original
        val isProfitable = price >= MIN_TOTAL_PRICE && pricePerKm >= MIN_PRICE_PER_KM && (minutes == 0 || pricePerMinute >= MIN_PRICE_PER_MINUTE)

        Log.d(TAG, "Profitability calculation - Price/km: %.2f (threshold: %.2f), Price/min: %.2f (threshold: %.2f), Min price: %.2f (threshold: %.2f)".format(
            pricePerKm, MIN_PRICE_PER_KM, pricePerMinute, MIN_PRICE_PER_MINUTE, price, MIN_TOTAL_PRICE))
        Log.d(TAG, "Is profitable: $isProfitable")
        return isProfitable
    }

    fun hideOverlay() {
        tripOverlay.hide()
    }

    companion object {
        private const val TAG = "TripAnalyzer"

        // Patrones de Regex (compilados para eficiencia)
        // Permite tanto punto como coma como separador decimal
        // Actualizado para manejar miles (ej: 13.525 o 13,525)
        private val PRICE_PATTERN: Pattern = Pattern.compile("(ARS|[$€£¥₡])?\\s*([\\d.,]+)")
        private val DISTANCE_PATTERN: Pattern = Pattern.compile("(\\d+(?:[.,]\\d)?)\\s*(km|mi)", Pattern.CASE_INSENSITIVE)
        private val TIME_PATTERN: Pattern = Pattern.compile("(\\d{1,2})\\s*min", Pattern.CASE_INSENSITIVE)
        private val RATING_PATTERN: Pattern = Pattern.compile("([\\d.,]+)\\s*\\(\\d+\\)")

        // Patrones específicos para Uber/Didi
        private val PICKUP_PATTERN: Pattern = Pattern.compile("A (\\d+)\\s*min.*\\((\\d+[,.]\\d+)\\s*km\\)", Pattern.CASE_INSENSITIVE)
        private val TRIP_PATTERN: Pattern = Pattern.compile("Viaje:\\s*(\\d+)\\s*min.*\\((\\d+[,.]\\d+)\\s*km\\)", Pattern.CASE_INSENSITIVE)

        // Palabras clave
        private val UBER_KEYWORDS = arrayOf("uber", "uberx", "comfort", "black", "suv", "xl")
        private val DIDI_KEYWORDS = arrayOf("didi", "di di", "express", "moto", "taxi", "premium")
        private val TRIP_ACTION_KEYWORDS = arrayOf("aceptar", "confirmar", "viaje", "recoger", "solicitar", "pedir", "iniciar", "accept", "confirm")

        // Umbrales de rentabilidad para ARS 10.000/hora (same as original)
        private const val MIN_PRICE_PER_MINUTE = 10000.0 / 60.0 // ~166.67 ARS por minuto
        private const val MIN_PRICE_PER_KM = 150.0 // Este valor es un ejemplo, ajústalo a tu costo por km
        private const val MIN_TOTAL_PRICE = 500.0 // Precio mínimo para considerar un viaje
    }
}