package com.rideanalyzer.app.util

import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class ImagePreprocessorTest {

    @Test
    fun `cropBottomHalf should create a bitmap with half the height`() {
        // Create a mock bitmap
        val originalBitmap = mock(Bitmap::class.java)
        `when`(originalBitmap.width).thenReturn(100)
        `when`(originalBitmap.height).thenReturn(200)
        
        // Call the method under test
        val croppedBitmap = ImagePreprocessor.cropBottomHalf(originalBitmap)
        
        // Verify the result
        assertNotNull(croppedBitmap)
        assertEquals(100, croppedBitmap.width)
        assertEquals(100, croppedBitmap.height) // Half of original height
    }
    
    @Test
    fun `isMostlyBlack should return true for a black bitmap`() {
        // Create a mock bitmap that simulates a black image
        val blackBitmap = mock(Bitmap::class.java)
        `when`(blackBitmap.width).thenReturn(100)
        `when`(blackBitmap.height).thenReturn(100)
        
        // Mock getPixel to return black pixels
        `when`(blackBitmap.getPixel(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(android.graphics.Color.BLACK)
        
        // Call the method under test
        val result = ImagePreprocessor.isMostlyBlack(blackBitmap)
        
        // Verify the result
        assertTrue(result)
    }
    
    @Test
    fun `isMostlyBlack should return false for a non-black bitmap`() {
        // Create a mock bitmap that simulates a non-black image
        val coloredBitmap = mock(Bitmap::class.java)
        `when`(coloredBitmap.width).thenReturn(100)
        `when`(coloredBitmap.height).thenReturn(100)
        
        // Mock getPixel to return non-black pixels
        `when`(coloredBitmap.getPixel(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(android.graphics.Color.WHITE)
        
        // Call the method under test
        val result = ImagePreprocessor.isMostlyBlack(coloredBitmap)
        
        // Verify the result
        assertFalse(result)
    }
}