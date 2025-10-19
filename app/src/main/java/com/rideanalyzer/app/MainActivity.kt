package com.rideanalyzer.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rideanalyzer.app.service.RideAccessibilityService
import com.rideanalyzer.app.service.ScreenshotService
import com.rideanalyzer.app.ui.TripOverlay
import com.rideanalyzer.app.util.AccessibilityServiceHelper

class MainActivity : Activity() {
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var startAccessibilityButton: Button
    private lateinit var startRecordingButton: Button
    private lateinit var stopAccessibilityButton: Button
    private lateinit var stopRecordingButton: Button
    private lateinit var startRecordingTestModeButton: Button
    private lateinit var grantPermissionsButton: Button
    private lateinit var reviewPermissionsButton: Button
    private lateinit var testOverlayButton: Button
    private lateinit var testTextOverlayButton: Button
    private var isAccessibilityServiceRunning = false
    private var isRecordingServiceRunning = false
    
    // 1. Definimos la lista de permisos en un solo lugar para evitar inconsistencias.
    private val requiredPermissions by lazy {
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            // Permisos adicionales según la versión de Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Initialize buttons
        startAccessibilityButton = findViewById(R.id.startAccessibilityButton)
        startRecordingButton = findViewById(R.id.startRecordingButton)
        stopAccessibilityButton = findViewById(R.id.stopAccessibilityButton)
        stopRecordingButton = findViewById(R.id.stopRecordingButton)
        startRecordingTestModeButton = findViewById(R.id.startRecordingTestModeButton)
        grantPermissionsButton = findViewById(R.id.grantPermissionsButton)
        reviewPermissionsButton = findViewById(R.id.reviewPermissionsButton)
        testOverlayButton = findViewById(R.id.testOverlayButton)
        testTextOverlayButton = findViewById(R.id.testTextOverlayButton)
        
        // Set up button listeners
        startAccessibilityButton.setOnClickListener {
            Log.d(TAG, "Start accessibility button clicked")
            // Check overlay permission first
            if (checkOverlayPermission()) {
                // Start accessibility service
                Toast.makeText(this@MainActivity, "Please enable the accessibility service in Settings", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } else {
                requestOverlayPermission()
            }
        }
        
        startRecordingButton.setOnClickListener {
            Log.d(TAG, "Start recording button clicked")
            // Check overlay permission first
            if (checkOverlayPermission()) {
                // Request screen capture permission
                startActivityForResult(
                    mediaProjectionManager.createScreenCaptureIntent(),
                    MEDIA_PROJECTION_REQUEST_CODE
                )
            } else {
                requestOverlayPermission()
            }
        }
        
        // New button for test mode
        startRecordingTestModeButton.setOnClickListener {
            Log.d(TAG, "Start recording test mode button clicked")
            // Check overlay permission first
            if (checkOverlayPermission()) {
                // Request screen capture permission with test mode flag
                val intent = mediaProjectionManager.createScreenCaptureIntent()
                intent.putExtra("testingMode", true)
                startActivityForResult(intent, MEDIA_PROJECTION_REQUEST_CODE)
            } else {
                requestOverlayPermission()
            }
        }
        
        stopAccessibilityButton.setOnClickListener {
            Log.d(TAG, "Stop accessibility button clicked")
            // Stop accessibility service (this is just a UI indicator, actual service is controlled by system)
            Toast.makeText(this@MainActivity, "Please disable the accessibility service in Settings", Toast.LENGTH_SHORT).show()
            isAccessibilityServiceRunning = false
            updateButtonStates()
        }
        
        stopRecordingButton.setOnClickListener {
            Log.d(TAG, "Stop recording button clicked")
            // Stop recording service
            stopScreenshotService()
        }
        
        grantPermissionsButton.setOnClickListener {
            Log.d(TAG, "Grant permissions button clicked")
            requestAllPermissions()
        }
        
        reviewPermissionsButton.setOnClickListener {
            Log.d(TAG, "Review permissions button clicked")
            // Abre la pantalla de configuración de la app para que el usuario pueda ajustar los permisos
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
        
        testOverlayButton.setOnClickListener {
            // Show a test overlay
            val testOverlay = TripOverlay(this@MainActivity)
            // Create a mock trip info for testing
            val mockTripInfo = com.rideanalyzer.app.model.TripInfo().apply {
                platform = "Test"
                price = 15000.0
                distance = 15.5
                estimatedMinutes = 25
                rating = 4.8
                isProfitable = true
                pricePerKm = 967.74
                pricePerMinute = 600.0
                pricePerHour = 36000.0
            }
            testOverlay.showTripAnalysis(mockTripInfo)
            
            // Don't hide automatically - let user close it manually
            // testOverlay.postDelayed({
            //     testOverlay.hide()
            // }, 5000)
        }
        
        // New button for testing text overlay
        testTextOverlayButton.setOnClickListener {
            // Show a test text overlay
            val testOverlay = TripOverlay(this@MainActivity)
            val testText = "This is a test of the text overlay functionality.\n\n" +
                    "It should display all detected text from the screen.\n\n" +
                    "Current time: ${System.currentTimeMillis()}\n\n" +
                    "This overlay should stay on screen and update with new content."
            testOverlay.showAllDetectedText(testText)
        }
        
        // Add a simple test to check if overlay permission is working
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            testOverlayPermission()
        }, 2000)
        
        updateButtonStates()
    }
    
    private fun testOverlayPermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        
        Log.d(TAG, "Overlay permission status: $hasPermission")
        Toast.makeText(this, "Overlay permission: $hasPermission", Toast.LENGTH_LONG).show()
        
        if (hasPermission) {
            // Try to show a simple overlay
            try {
                val testOverlay = TripOverlay(this)
                val mockTripInfo = com.rideanalyzer.app.model.TripInfo().apply {
                    platform = "Permission Test"
                    price = 1000.0
                    distance = 5.0
                    estimatedMinutes = 10
                    isProfitable = true
                }
                testOverlay.showTripAnalysis(mockTripInfo)
                Log.d(TAG, "Test overlay shown successfully")
                
                // Hide it after 3 seconds
                testOverlay.postDelayed({
                    testOverlay.hide()
                }, 3000)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing test overlay", e)
                Toast.makeText(this, "Error showing test overlay: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check if accessibility service is running using our helper
        isAccessibilityServiceRunning = AccessibilityServiceHelper.isRideAccessibilityServiceEnabled(this)
        // 2. onResume es ahora el ÚNICO punto de entrada para verificar permisos.
        // Esto asegura un flujo predecible cada vez que la app pasa a primer plano.
        checkPermissionsAndShowUI()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            // Volvemos de la pantalla de permisos de superposición
            // onResume se encargará de re-evaluar el estado, no necesitamos hacer nada aquí
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission is required to show trip info", Toast.LENGTH_LONG).show()
            }
        }

        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "Media projection permission granted, starting service")
                // Check if we're in test mode
                val isTestMode = data.getBooleanExtra("testingMode", false)
                Log.d(TAG, "Test mode: $isTestMode")
                
                // Permiso concedido, iniciamos el servicio real
                val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", Intent(data)) // Create a new Intent with the data
                    putExtra("testingMode", isTestMode) // Pass test mode flag
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Iniciando servicio de grabación...", Toast.LENGTH_SHORT).show()
                isRecordingServiceRunning = true
                updateButtonStates()
            } else {
                Toast.makeText(this, "Screen capture permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun checkPermissionsAndShowUI() {
        // Flujo de permisos secuencial y claro:
        // a) ¿Tenemos permiso de superposición? Si no, lo pedimos.
        if (!Settings.canDrawOverlays(this)) {
            updateButtonStates() // Muestra el botón "Conceder Permisos"
            return // Salimos para que el usuario actúe
        }
        // b) ¿Tenemos los demás permisos? Si no, los pedimos.
        checkAndRequestRuntimePermissions()
        // Note: updateButtonStates() will be called after permissions are granted
    }
    
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    private fun requestOverlayPermission() {
        Log.d(TAG, "Requesting overlay permission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
    
    private fun requestAllPermissions() {
        requestOverlayPermission()
        checkAndRequestRuntimePermissions()
        requestBatteryOptimizationExemption()
    }
    
    private fun stopScreenshotService() {
        val serviceIntent = Intent(this, ScreenshotService::class.java)
        stopService(serviceIntent)
        isRecordingServiceRunning = false
        updateButtonStates()
    }
    
    private fun checkAndRequestRuntimePermissions() {
        // 3. Usamos la lista única de permisos para ver cuáles faltan.
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Permissions to request: $permissionsToRequest")
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting runtime permissions: $permissionsToRequest")
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), RUNTIME_PERMISSIONS_REQUEST_CODE)
        } else {
            Log.d(TAG, "All runtime permissions are already granted.")
            // If all permissions are granted, check battery optimization
            requestBatteryOptimizationExemption()
            // Si ya tenemos todos los permisos, actualizamos la UI
            updateButtonStates()
        }
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request battery optimization exemption", e)
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RUNTIME_PERMISSIONS_REQUEST_CODE) {
            // 4. Después de que el usuario responde, simplemente actualizamos la UI. onResume se encargará si es necesario.
            Log.d(TAG, "Finished runtime permission request. Updating UI.")
            updateButtonStates()
        }
    }
    
    private fun updateButtonStates() {
        val allPermissionsGranted = checkAllPermissionsGranted()
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        
        // Update accessibility service status
        isAccessibilityServiceRunning = AccessibilityServiceHelper.isRideAccessibilityServiceEnabled(this)
        
        Log.d(TAG, "updateButtonStates: hasOverlayPermission=$hasOverlayPermission, allPermissionsGranted=$allPermissionsGranted, isAccessibilityServiceRunning=$isAccessibilityServiceRunning")

        // Lógica de visibilidad de botones
        if (!hasOverlayPermission) {
            // Si falta el permiso de superposición, es lo primero que se debe conceder
            Log.d(TAG, "Showing grant permissions button (missing overlay permission)")
            grantPermissionsButton.visibility = Button.VISIBLE
            reviewPermissionsButton.visibility = Button.GONE
            startAccessibilityButton.visibility = Button.GONE
            startRecordingButton.visibility = Button.GONE
            stopAccessibilityButton.visibility = Button.GONE
            stopRecordingButton.visibility = Button.GONE
        } else if (!allPermissionsGranted) {
            // Si tiene superposición pero faltan otros permisos, mostramos el botón de revisar
            Log.d(TAG, "Showing review permissions button (missing runtime permissions)")
            grantPermissionsButton.visibility = Button.GONE
            reviewPermissionsButton.visibility = Button.VISIBLE
            startAccessibilityButton.visibility = Button.GONE
            startRecordingButton.visibility = Button.GONE
            stopAccessibilityButton.visibility = Button.GONE
            stopRecordingButton.visibility = Button.GONE
        } else {
            // Si todos los permisos están concedidos, mostramos los botones de control
            Log.d(TAG, "Showing all control buttons (all permissions granted)")
            grantPermissionsButton.visibility = Button.GONE
            reviewPermissionsButton.visibility = Button.GONE
            startAccessibilityButton.visibility = Button.VISIBLE
            startRecordingButton.visibility = Button.VISIBLE
            stopAccessibilityButton.visibility = Button.VISIBLE
            stopRecordingButton.visibility = Button.VISIBLE
            
            // Update button states based on service status
            startAccessibilityButton.isEnabled = !isAccessibilityServiceRunning
            stopAccessibilityButton.isEnabled = isAccessibilityServiceRunning
            startRecordingButton.isEnabled = !isRecordingServiceRunning
            stopRecordingButton.isEnabled = isRecordingServiceRunning
        }
    }
    
    private fun checkAllPermissionsGranted(): Boolean {
        // 5. Usamos la lista única para verificar si todos los permisos están concedidos.
        val allGranted = requiredPermissions.all {
            val granted = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $it granted: $granted")
            granted
        }
        Log.d(TAG, "All required permissions granted: $allGranted")
        return allGranted
    }
    
    companion object {
        private const val TAG = "MainActivity"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 101
        private const val MEDIA_PROJECTION_REQUEST_CODE = 102
        private const val RUNTIME_PERMISSIONS_REQUEST_CODE = 103
    }
}