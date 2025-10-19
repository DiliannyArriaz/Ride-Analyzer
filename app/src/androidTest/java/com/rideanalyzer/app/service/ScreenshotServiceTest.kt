package com.rideanalyzer.app.service

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rideanalyzer.app.util.ImagePreprocessor
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ScreenshotServiceTest {

    @Test
    fun `cropBottomHalf should correctly crop bitmap to bottom half`() {
        // Create a mock bitmap
        val originalBitmap = mock(Bitmap::class.java)
        `when`(originalBitmap.width).thenReturn(200)
        `when`(originalBitmap.height).thenReturn(400)
        
        // Call the ImagePreprocessor method
        val croppedBitmap = ImagePreprocessor.cropBottomHalf(originalBitmap)
        
        // Verify the result has correct dimensions
        assertNotNull(croppedBitmap)
        assertEquals(200, croppedBitmap.width)
        assertEquals(200, croppedBitmap.height) // Half of 400
    }
}