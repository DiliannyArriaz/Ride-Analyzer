package com.rideanalyzer.app.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rideanalyzer.app.analyzer.TripAnalyzer
import com.rideanalyzer.app.util.ImagePreprocessor
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class ScreenshotService : Service() {
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var tripAnalyzer: TripAnalyzer
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val isCapturing: AtomicBoolean = AtomicBoolean(false)
    private var lastImageHash: Int = 0
    private var isAnalyzing: Boolean = false
    private var showAllTextForTesting: Boolean = false // Flag for testing mode
    private var lastOcrAttemptTime: Long = 0
    private val OCR_DEBOUNCE_INTERVAL_MS: Long = 900L // 900ms debounce
    // Percentage of the screen height where cropping starts (0.0..1.0)
    private var cropStartPercent: Float = 0.30f // 30% from top by default (increased area)
    
    private lateinit var captureRunnable: Runnable
    
    override fun onCreate() {
        super.onCreate()
        tripAnalyzer = TripAnalyzer(this)
        Log.d(TAG, "ScreenshotService created")
        
        // Initialize the capture runnable
        captureRunnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "Capture runnable triggered")
                captureScreen()
                if (isCapturing.get()) {
                    handler.postDelayed(this, CAPTURE_INTERVAL_MS)
                }
            }
        }
        
        // Start as foreground service with persistent notification
        createNotificationChannel()
        startForeground(SERVICE_ID, createPersistentNotification("Ride Analyzer", "Screen capture service running"))
        Log.d(TAG, "ScreenshotService started as foreground service")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with intent: $intent")
        Log.d(TAG, "onStartCommand called with flags: $flags, startId: $startId")
        intent?.let {
            val resultCode = it.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra("data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra("data")
            }
            
            // Check for testing mode flag
            val testingMode = it.getBooleanExtra("testingMode", false)
            showAllTextForTesting = testingMode
            tripAnalyzer.enableShowAllTextForTesting(testingMode)
            
            // Get desired hourly rate from intent
            val desiredHourlyRate = it.getDoubleExtra("desiredHourlyRate", 10000.0)
            // Pass this to TripAnalyzer
            tripAnalyzer.desiredHourlyRate = desiredHourlyRate
            Log.d(TAG, "Desired hourly rate set to: $desiredHourlyRate ARS/hour")
            Log.d(TAG, "Verified TripAnalyzer desiredHourlyRate: ${tripAnalyzer.desiredHourlyRate}")
            
            Log.d(TAG, "Received resultCode: $resultCode")
            Log.d(TAG, "Received data: $data")
            Log.d(TAG, "Testing mode: $testingMode")
            
            // Check if resultCode is RESULT_OK (0) which means permission was granted
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "Starting screen capture with result code: $resultCode")
                startScreenCapture(resultCode, data)
            } else {
                Log.e(TAG, "Invalid result code or data for screen capture")
                Log.e(TAG, "Result code check: ${resultCode == Activity.RESULT_OK}")
                Log.e(TAG, "Data check: ${data != null}")
                Log.e(TAG, "Expected RESULT_OK: ${Activity.RESULT_OK}, Received: $resultCode")
            }
        }
        
        return START_STICKY
    }
    
    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        
        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi
        
        Log.d(TAG, "Starting screen capture: ${width}x${height} @ ${density}dpi")
        
        // Create ImageReader
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        // Create VirtualDisplay
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            0,
            imageReader?.surface,
            null,
            null
        )
        
        // Start capturing
        isCapturing.set(true)
        handler.post(captureRunnable)
        
        updateNotification("Ride Analyzer", "Actively analyzing screen for ride offers...", true)
        Log.d(TAG, "Screen capture started")
    }
    
    private fun captureScreen() {
        Log.d(TAG, "captureScreen called")
        val image = imageReader?.acquireLatestImage()
        
        if (image == null) {
            Log.d(TAG, "No image available from ImageReader")
            return
        }
        
        Log.d(TAG, "Image acquired: ${image.width}x${image.height}, format: ${image.format}")
        
        try {
            // Convert Image to Bitmap
            val bitmap = imageToBitmap(image)
            
            if (bitmap != null) {
                Log.d(TAG, "Bitmap created: ${bitmap.width}x${bitmap.height}")
                
                // Try multiple cropping strategies for better detection
                val croppedBitmap = tryMultipleCroppingStrategies(bitmap)
                Log.d(TAG, "Cropped bitmap: ${croppedBitmap.width}x${croppedBitmap.height} (startPercent=$cropStartPercent)")
                
                // Create a simple hash to detect if the image has changed significantly
                val currentHash = croppedBitmap.hashCode()
                val hashDifference = abs(currentHash - lastImageHash)
                
                Log.d(TAG, "Region bottom-half hash=$currentHash, Last: $lastImageHash, Difference: $hashDifference")
                
                // Check if the cropped region is mostly black (FLAG_SECURE detection)
                if (ImagePreprocessor.isMostlyBlack(croppedBitmap)) {
                    Log.d(TAG, "Skipping OCR (black) - capture_blocked_flag_secure")
                    // Save debug image for inspection
                    saveDebugImage(croppedBitmap, "black_screen")
                    // Recycle bitmaps
                    if (!croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    image.close()
                    return
                }
                
                // Save debug image for inspection
                saveDebugImage(croppedBitmap, "ocr_input")
                
                // Only process if the image has changed significantly
                if (hashDifference > IMAGE_CHANGE_THRESHOLD) {
                    lastImageHash = currentHash
                    Log.d(TAG, "Image changed significantly, processing...")
                    
                    // Check OCR debounce
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastOcrAttemptTime < OCR_DEBOUNCE_INTERVAL_MS) {
                        Log.d(TAG, "Skipping OCR due to debounce - ${currentTime - lastOcrAttemptTime}ms since last attempt")
                        // Save debug image for inspection
                        saveDebugImage(croppedBitmap, "debounced")
                        // Recycle bitmaps
                        if (!croppedBitmap.isRecycled) {
                            croppedBitmap.recycle()
                        }
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                        image.close()
                        return
                    }
                    
                    // Update last OCR attempt time
                    lastOcrAttemptTime = currentTime
                    Log.d(TAG, "OCR attempt start")
                    
                    // Indicate we're analyzing
                    isAnalyzing = true
                    updateNotification("Ride Analyzer", "Analyzing screen content...", true)
                    
                    Log.d(TAG, "Sending cropped screenshot to analyzer, bitmap size: ${croppedBitmap.width}x${croppedBitmap.height}")
                    
                    // Analyze the captured screen (using cropped bitmap)
                    tripAnalyzer.analyzeScreenshot(croppedBitmap) { tripInfo ->
                        isAnalyzing = false
                        if (tripInfo != null) {
                            Log.d(TAG, "Trip info detected: $tripInfo")
                            updateNotification("Ride Analyzer - Trip Detected", "Analyzing ride offers... Tap to open app", true)
                        } else {
                            Log.d(TAG, "No trip info detected in screenshot")
                            tripAnalyzer.hideOverlay() // Hide the overlay when no trip is detected
                            updateNotification("Ride Analyzer", "Actively analyzing screen for ride offers...", true)
                        }
                    }

                } else {
                    Log.d(TAG, "Image hasn't changed significantly, skipping analysis")
                    // Image hasn't changed significantly, recycle bitmaps
                    if (!croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            } else {
                Log.e(TAG, "Failed to convert image to bitmap")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing screenshot", e)
            isAnalyzing = false
            updateNotification("Ride Analyzer", "Actively analyzing screen for ride offers...", true)
        } finally {
            image.close()
        }
    }
    
    /**
     * Try multiple cropping strategies to improve OCR accuracy
     */
    private fun tryMultipleCroppingStrategies(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "Trying multiple cropping strategies")
        
        // Strategy 1: Bottom half (default)
        val cropped1 = ImagePreprocessor.cropBottomHalf(bitmap, cropStartPercent)
        Log.d(TAG, "Strategy 1 (Bottom half): ${cropped1.width}x${cropped1.height}")
        
        // Strategy 2: Middle region (more focused)
        val cropped2 = ImagePreprocessor.cropMiddleRegion(bitmap, 0.25f, 0.65f)
        Log.d(TAG, "Strategy 2 (Middle region): ${cropped2.width}x${cropped2.height}")
        
        // Strategy 3: Larger bottom region
        val cropped3 = ImagePreprocessor.cropBottomHalf(bitmap, 0.20f)
        Log.d(TAG, "Strategy 3 (Larger bottom): ${cropped3.width}x${cropped3.height}")
        
        // For now, return the default strategy but log all options
        // In the future, we could implement logic to choose the best strategy
        return cropped1
    }
    
    private fun imageToBitmap(image: Image): Bitmap? {
        try {
            Log.d(TAG, "Converting image to bitmap: ${image.width}x${image.height}")
            val width = image.width
            val height = image.height
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelData = ByteArray(buffer.remaining())
            buffer.get(pixelData)
            
            Log.d(TAG, "Pixel data size: ${pixelData.size} bytes")
            
            // Create bitmap from pixel data
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Copy pixel data to bitmap
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixelData))
            
            Log.d(TAG, "Bitmap created successfully: ${bitmap.width}x${bitmap.height}")
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to bitmap", e)
            return null
        }
    }
    
    private fun processImage(bitmap: Bitmap) {
        Log.d(TAG, "=== PROCESSING IMAGE ===")
        Log.d(TAG, "Original bitmap size: ${bitmap.width}x${bitmap.height}")
        
        // Check if the screen is secure/blocked
        if (isMostlyBlack(bitmap)) {
            Log.d(TAG, "Screen appears to be secure/blocked, skipping OCR processing")
            isAnalyzing = false
            return
        }

        // Crop to bottom half of screen for efficiency and privacy
        val croppedBitmap = cropBottomHalf(bitmap)
        Log.d(TAG, "Cropped bitmap size: ${croppedBitmap.width}x${croppedBitmap.height}")
        
        // Save debug image for inspection
        saveDebugImage(croppedBitmap)
        
        // Check if the cropped image is empty
        if (isBitmapEmpty(croppedBitmap)) {
            Log.d(TAG, "Cropped image appears to be empty, skipping OCR processing")
            isAnalyzing = false
            return
        }

        Log.d(TAG, "Sending cropped screenshot to analyzer, bitmap size: ${croppedBitmap.width}x${croppedBitmap.height}")
        
        // Analyze the captured screen (using cropped bitmap)
        tripAnalyzer.analyzeScreenshot(croppedBitmap) { tripInfo ->
            isAnalyzing = false
            if (tripInfo != null) {
                Log.d(TAG, "Trip info detected: $tripInfo")
                updateNotification("Ride Analyzer - Trip Detected", "Analyzing ride offers... Tap to open app", true)
            } else {
                Log.d(TAG, "No trip info detected in screenshot")
                tripAnalyzer.hideOverlay() // Hide the overlay when no trip is detected
                updateNotification("Ride Analyzer", "Actively analyzing screen for ride offers...", true)
            }
        }
    }

    private fun saveDebugImage(bitmap: Bitmap, prefix: String = "debug") {
        try {
            val debugDir = File(getExternalFilesDir(null), "debug_ocr")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val file = File(debugDir, "${prefix}_${timestamp}.png")
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Log.d(TAG, "Saved debug image to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving debug image", e)
        }
    }
    
    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        // Check if a significant portion of the bitmap is black
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        var blackPixels = 0
        
        // Check every 5th pixel
        for (x in 0 until width step 5) {
            for (y in 0 until height step 5) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                
                if (red < 20 && green < 20 && blue < 20) {
                    blackPixels++
                }
            }
        }
        
        // Calculate the percentage of black pixels
        val blackPixelPercentage = (blackPixels.toDouble() / totalPixels) * 100
        return blackPixelPercentage > 90.0 // 90% threshold for black screen
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
        
        // If more than 95% of sampled pixels are black, consider it empty
        // Increased threshold to better detect when screen content is minimal
        val isEmpty = totalPixels > 0 && (blackPixels.toDouble() / totalPixels) > 0.95
        Log.d(TAG, "Bitmap empty check - Total pixels sampled: $totalPixels, Black pixels: $blackPixels, Is empty: $isEmpty")
        return isEmpty
    }

    private fun cropBottomHalf(bitmap: Bitmap): Bitmap {
        val height = bitmap.height
        val cropHeight = (height * 0.5).toInt() // Crop bottom half
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            height - cropHeight,
            bitmap.width,
            cropHeight
        )
        return croppedBitmap
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ScreenshotService onDestroy called")
        stopScreenCapture()
        Log.d(TAG, "ScreenshotService destroyed")
    }
    
    private fun stopScreenCapture() {
        Log.d(TAG, "Stopping screen capture")
        isCapturing.set(false)
        handler.removeCallbacks(captureRunnable)
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        updateNotification("Ride Analyzer", "Screen capture service stopped", false)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called with intent: $intent")
        return null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createPersistentNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true) // Make it persistent
            .build()
    }
    
    private fun updateNotification(title: String, content: String, ongoing: Boolean = true) {
        Log.d(TAG, "Updating notification: $title - $content")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(ongoing) // Make it persistent
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_ID, notification)
    }
    
    companion object {
        private const val TAG = "ScreenshotService"
        private const val SERVICE_ID = 1001
        private const val CHANNEL_ID = "ScreenCaptureChannel"
        private const val CAPTURE_INTERVAL_MS: Long = 2000L // Increased to 2 seconds to reduce load on low-end devices
        private const val IMAGE_CHANGE_THRESHOLD: Int = 50000 // Increased threshold to reduce false positives
    }
}