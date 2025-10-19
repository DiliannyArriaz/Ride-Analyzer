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
    private val handler = Handler(Looper.getMainLooper())
    private val isCapturing = AtomicBoolean(false)
    private var lastImageHash: Int = 0
    private var isAnalyzing = false
    private var showAllTextForTesting = false // Flag for testing mode
    private var lastOcrAttemptTime: Long = 0
    private val OCR_DEBOUNCE_INTERVAL_MS = 900L // 900ms debounce
    
    private val captureRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Capture runnable triggered")
            captureScreen()
            if (isCapturing.get()) {
                handler.postDelayed(this, CAPTURE_INTERVAL_MS)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        tripAnalyzer = TripAnalyzer(this)
        Log.d(TAG, "ScreenshotService created")
        
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
                
                // Crop to bottom half
                val croppedBitmap = ImagePreprocessor.cropBottomHalf(bitmap)
                Log.d(TAG, "Cropped bitmap: ${croppedBitmap.width}x${croppedBitmap.height}")
                
                // Create a simple hash to detect if the image has changed significantly
                val currentHash = croppedBitmap.hashCode()
                val hashDifference = abs(currentHash - lastImageHash)
                
                Log.d(TAG, "Region bottom-half hash=$currentHash, Last: $lastImageHash, Difference: $hashDifference")
                
                // Check if the cropped region is mostly black (FLAG_SECURE detection)
                if (ImagePreprocessor.isMostlyBlack(croppedBitmap)) {
                    Log.d(TAG, "Skipping OCR (black) - capture_blocked_flag_secure")
                    // Save debug image if in debug mode
                    if (BuildConfig.DEBUG) {
                        saveDebugImage(croppedBitmap, "bottom_crop")
                    }
                    // Recycle bitmaps
                    if (!croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    return
                }
                
                // Only process if the image has changed significantly
                if (hashDifference > IMAGE_CHANGE_THRESHOLD) {
                    lastImageHash = currentHash
                    Log.d(TAG, "Image changed significantly, processing...")
                    
                    // Check OCR debounce
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastOcrAttemptTime < OCR_DEBOUNCE_INTERVAL_MS) {
                        Log.d(TAG, "Skipping OCR due to debounce - ${currentTime - lastOcrAttemptTime}ms since last attempt")
                        // Save debug image if in debug mode
                        if (BuildConfig.DEBUG) {
                            saveDebugImage(croppedBitmap, "bottom_crop")
                        }
                        // Recycle bitmaps
                        if (!croppedBitmap.isRecycled) {
                            croppedBitmap.recycle()
                        }
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                        return
                    }
                    
                    // Update last OCR attempt time
                    lastOcrAttemptTime = currentTime
                    Log.d(TAG, "OCR attempt start")
                    
                    // Save debug image if in debug mode
                    if (BuildConfig.DEBUG) {
                        saveDebugImage(croppedBitmap, "bottom_crop")
                    }
                    
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
                            updateNotification("Ride Analyzer", "Actively analyzing screen for ride offers...", true)
                        }
                    }
                } else {
                    Log.d(TAG, "Image hasn't changed significantly, skipping analysis")
                    // Save debug image if in debug mode
                    if (BuildConfig.DEBUG) {
                        saveDebugImage(croppedBitmap, "bottom_crop")
                    }
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
    
    private fun saveDebugImage(bitmap: Bitmap, prefix: String) {
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
        private const val CAPTURE_INTERVAL_MS = 2000L // 2 seconds for better detection
        private const val IMAGE_CHANGE_THRESHOLD = 50000 // Lower threshold for better sensitivity
    }
}