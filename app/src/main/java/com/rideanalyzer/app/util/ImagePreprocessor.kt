package com.rideanalyzer.app.util

import android.graphics.Bitmap
import android.util.Log

class ImagePreprocessor {
    companion object {
        private const val TAG = "ImagePreprocessor"
        
        /**
         * Crops the bottom half of the bitmap
         */
        fun cropBottomHalf(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val top = height / 2
            
            Log.d(TAG, "Cropping bitmap to bottom half - Original: ${width}x$height, Crop: ${width}x${height - top} at (0,$top)")
            
            // Create a new bitmap with just the bottom half
            return Bitmap.createBitmap(bitmap, 0, top, width, height - top)
        }
        
        /**
         * Checks if a bitmap is mostly black
         */
        fun isMostlyBlack(bitmap: Bitmap, threshold: Double = 0.95): Boolean {
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
            
            // If more than threshold percentage of sampled pixels are black, consider it empty
            val isMostlyBlack = totalPixels > 0 && (blackPixels.toDouble() / totalPixels) > threshold
            Log.d(TAG, "Bitmap black check - Total pixels sampled: $totalPixels, Black pixels: $blackPixels, Is mostly black: $isMostlyBlack")
            return isMostlyBlack
        }
    }
}