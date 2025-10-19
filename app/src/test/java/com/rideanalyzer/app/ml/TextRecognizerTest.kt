package com.rideanalyzer.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.Mockito.*
import org.junit.Assert.*
import org.mockito.ArgumentMatchers.any
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

@RunWith(RobolectricTestRunner::class)
class TextRecognizerTest {

    @Test
    fun `recognizeText should respect debounce interval`() = runBlocking {
        // Create a mock TextRecognizer
        val mockRecognizer = mock(TextRecognizer::class.java)
        val textRecognizer = TextRecognizer()
        
        // Use reflection to access the private recognizer field
        val recognizerField = TextRecognizer::class.java.getDeclaredField("recognizer")
        recognizerField.isAccessible = true
        recognizerField.set(textRecognizer, mockRecognizer)
        
        // Create a mock InputImage
        val mockImage = mock(InputImage::class.java)
        
        // Create a mock Text result
        val mockText = mock(Text::class.java)
        `when`(mockText.text).thenReturn("Test text")
        
        // Create a mock Task
        val mockTask = mock(Task::class.java) as Task<Text>
        `when`(mockTask.await()).thenReturn(mockText)
        
        // Mock the process method to return our task
        `when`(mockRecognizer.process(any(InputImage::class.java))).thenReturn(mockTask)
        
        // Call recognizeText for the first time
        val result1 = textRecognizer.recognizeText(mockImage)
        
        // Immediately call it again (should be debounced)
        val result2 = textRecognizer.recognizeText(mockImage)
        
        // First call should return text, second should be debounced (empty)
        assertNotEquals("", result1)
        assertEquals("", result2)
    }
}