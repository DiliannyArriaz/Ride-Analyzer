package com.rideanalyzer.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rideanalyzer.app.R
import com.rideanalyzer.app.service.RideAccessibilityService
import com.rideanalyzer.app.service.ScreenshotService
import com.rideanalyzer.app.ui.TripOverlay
import com.rideanalyzer.app.util.AccessibilityServiceHelper

class MainActivity : Activity() {
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var grantPermissionsButton: MaterialButton
    private lateinit var startAccessibilityButton: MaterialButton
    private lateinit var desiredHourlyRateInput: TextInputEditText
    private lateinit var saveHourlyRateButton: MaterialButton
    private lateinit var startRecordingButton: MaterialButton
    private lateinit var systemStatusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var sharedPreferences: SharedPreferences
    private var isAccessibilityServiceRunning = false
    private var isRecordingServiceRunning = false
    
    // SharedPreferences key for saving the desired hourly rate
    private val PREFS_NAME = "RideAnalyzerPrefs"
    private val KEY_DESIRED_HOURLY_RATE = "desired_hourly_rate"
    
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
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Initialize UI elements
        grantPermissionsButton = findViewById(R.id.grantPermissionsButton)
        startAccessibilityButton = findViewById(R.id.startAccessibilityButton)
        desiredHourlyRateInput = findViewById(R.id.desiredHourlyRateInput)
        saveHourlyRateButton = findViewById(R.id.saveHourlyRateButton)
        startRecordingButton = findViewById(R.id.startRecordingButton)
        systemStatusText = findViewById(R.id.systemStatusText)
        statusIndicator = findViewById(R.id.statusIndicator)
        
        // Load saved desired hourly rate or set default value
        val savedHourlyRate = sharedPreferences.getFloat(KEY_DESIRED_HOURLY_RATE, 10000f)
        desiredHourlyRateInput.setText(savedHourlyRate.toInt().toString())
        
        // Set up button listeners
        grantPermissionsButton.setOnClickListener {
            Log.d(TAG, "Grant permissions button clicked")
            requestAllPermissions()
        }
        
        startAccessibilityButton.setOnClickListener {
            Log.d(TAG, "Start accessibility button clicked")
            // Check if accessibility service is already running
            if (AccessibilityServiceHelper.isRideAccessibilityServiceEnabled(this)) {
                // If already running, take user to accessibility settings to disable it
                Toast.makeText(this@MainActivity, "Taking you to accessibility settings to disable service", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } else {
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
        }
        
        saveHourlyRateButton.setOnClickListener {
            Log.d(TAG, "Save hourly rate button clicked")
            saveDesiredHourlyRate()
        }
        
        startRecordingButton.setOnClickListener {
            Log.d(TAG, "Start recording button clicked")
            if (isRecordingServiceRunning) {
                // Stop the service
                stopScreenshotService()
                startRecordingButton.text = "INICIAR"
                startRecordingButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_cyan)
                systemStatusText.text = "Sistema listo para analizar"
                statusIndicator.visibility = View.GONE
            } else {
                // Start the service
                // Check overlay permission first
                if (checkOverlayPermission()) {
                    // Get desired hourly rate from input field
                    val desiredHourlyRate = try {
                        desiredHourlyRateInput.text.toString().toDouble()
                    } catch (e: NumberFormatException) {
                        10000.0 // Default value if input is invalid
                    }
                    
                    // Save the desired hourly rate
                    saveDesiredHourlyRate()
                    
                    // Request screen capture permission
                    val intent = mediaProjectionManager.createScreenCaptureIntent()
                    startActivityForResult(intent, MEDIA_PROJECTION_REQUEST_CODE)
                } else {
                    requestOverlayPermission()
                }
            }
        }
        
        updateButtonStates()
    }
    
    /**
     * Save the desired hourly rate to SharedPreferences
     */
    private fun saveDesiredHourlyRate() {
        val hourlyRateText = desiredHourlyRateInput.text.toString()
        if (hourlyRateText.isNotEmpty()) {
            try {
                val hourlyRate = hourlyRateText.toFloat()
                sharedPreferences.edit()
                    .putFloat(KEY_DESIRED_HOURLY_RATE, hourlyRate)
                    .apply()
                Toast.makeText(this, "Valor guardado: $hourlyRate ARS/hora", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Saved desired hourly rate: $hourlyRate")
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Por favor ingrese un valor numérico válido", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Invalid number format for hourly rate: $hourlyRateText", e)
            }
        } else {
            Toast.makeText(this, "Por favor ingrese un valor", Toast.LENGTH_SHORT).show()
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
                
                // Get desired hourly rate from input field
                val desiredHourlyRate = try {
                    desiredHourlyRateInput.text.toString().toDouble()
                } catch (e: NumberFormatException) {
                    10000.0 // Default value if input is invalid
                }
                
                // Save the desired hourly rate
                saveDesiredHourlyRate()
                
                // Permiso concedido, iniciamos el servicio real
                val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", Intent(data)) // Create a new Intent with the data
                    putExtra("desiredHourlyRate", desiredHourlyRate) // Pass the desired hourly rate to the service
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Iniciando servicio de grabación...", Toast.LENGTH_SHORT).show()
                isRecordingServiceRunning = true
                startRecordingButton.text = "APAGAR"
                startRecordingButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_red)
                systemStatusText.text = "Analizando viajes"
                statusIndicator.visibility = View.VISIBLE
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
        systemStatusText.text = "Sistema listo para analizar"
        statusIndicator.visibility = View.GONE
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

        // Update the accessibility button text based on service status
        if (isAccessibilityServiceRunning) {
            startAccessibilityButton.text = "Accesibilidad activa"
        } else {
            startAccessibilityButton.text = "Activar Accesibilidad"
        }

        // Update the recording button text and color based on service status
        if (isRecordingServiceRunning) {
            startRecordingButton.text = "APAGAR"
            startRecordingButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_red)
            systemStatusText.text = "Analizando viajes"
            statusIndicator.visibility = View.VISIBLE
        } else {
            startRecordingButton.text = "INICIAR"
            startRecordingButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.neon_cyan)
            if (hasOverlayPermission && allPermissionsGranted) {
                systemStatusText.text = "Sistema listo para analizar"
            } else {
                systemStatusText.text = "Sistema en espera"
            }
            statusIndicator.visibility = View.GONE
        }

        // Lógica de visibilidad de botones
        if (!hasOverlayPermission) {
            // Si falta el permiso de superposición, es lo primero que se debe conceder
            Log.d(TAG, "Showing grant permissions button (missing overlay permission)")
            grantPermissionsButton.visibility = MaterialButton.VISIBLE
            startAccessibilityButton.visibility = MaterialButton.GONE
        } else if (!allPermissionsGranted) {
            // Si tiene superposición pero faltan otros permisos, mostramos el botón de revisar
            Log.d(TAG, "Showing grant permissions button (missing runtime permissions)")
            grantPermissionsButton.visibility = MaterialButton.VISIBLE
            startAccessibilityButton.visibility = MaterialButton.GONE
        } else {
            // Si todos los permisos están concedidos, ocultamos el botón de permisos y mostramos el de accesibilidad
            Log.d(TAG, "Hiding grant permissions button (all permissions granted)")
            grantPermissionsButton.visibility = MaterialButton.GONE
            startAccessibilityButton.visibility = MaterialButton.VISIBLE
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