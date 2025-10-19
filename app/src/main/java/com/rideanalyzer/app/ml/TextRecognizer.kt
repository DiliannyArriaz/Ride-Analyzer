package com.rideanalyzer.app.ml

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class TextRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val tag = "TextRecognizer"
    private var lastRecognitionTime: Long = 0
    private val RECOGNITION_DEBOUNCE_INTERVAL_MS = 900L // 900ms debounce

    suspend fun recognizeText(image: InputImage): String {
        // Check debounce
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRecognitionTime < RECOGNITION_DEBOUNCE_INTERVAL_MS) {
            Log.d(tag, "Skipping OCR due to debounce - ${currentTime - lastRecognitionTime}ms since last attempt")
            return ""
        }
        
        // Update last recognition time
        lastRecognitionTime = currentTime
        Log.d(tag, "OCR attempt start")
        
        return try {
            val result = recognizer.process(image).await()
            Log.d(tag, "OCR result length=${result.text.length}")
            result.text
        } catch (e: Exception) {
            Log.e(tag, "Error recognizing text", e)
            "Error recognizing text: ${e.message}"
        }
    }
}