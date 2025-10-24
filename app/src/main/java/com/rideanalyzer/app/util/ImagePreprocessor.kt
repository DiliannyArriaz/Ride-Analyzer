package com.rideanalyzer.app.util

import android.graphics.Bitmap
import android.util.Log

class ImagePreprocessor {
    companion object {
        private const val TAG = "ImagePreprocessor"
        
        /**
         * Crops the bottom region of the bitmap starting at a percentage of the height.
         * By default we take from 40% of the screen height to the bottom to include
         * slightly more area above the half (helps with larger Didi notifications).
         */
        fun cropBottomHalf(bitmap: Bitmap, startPercent: Float = 0.40f): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            // Ensure percent is within (0..1)
            val pct = when {
                startPercent <= 0f -> 0.0f
                startPercent >= 1f -> 0.5f
                else -> startPercent
            }
            val top = (height * pct).toInt()

            Log.d(TAG, "Cropping bitmap from ${pct * 100}% - Original: ${width}x$height, Crop: ${width}x${height - top} at (0,$top)")

            // Create a new bitmap from the computed top to the bottom
            return Bitmap.createBitmap(bitmap, 0, top, width, height - top)
        }
        
        /**
         * Crops a middle region of the bitmap between startPercent and endPercent of the height.
         */
        fun cropMiddleRegion(bitmap: Bitmap, startPercent: Float, endPercent: Float): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            
            // Ensure percents are within (0..1) and start < end
            val start = when {
                startPercent <= 0f -> 0.0f
                startPercent >= 1f -> 0.0f
                else -> startPercent
            }
            
            val end = when {
                endPercent <= 0f -> 1.0f
                endPercent >= 1f -> 1.0f
                else -> endPercent
            }
            
            val top = (height * start).toInt()
            val bottom = (height * end).toInt()
            val cropHeight = bottom - top

            Log.d(TAG, "Cropping middle region from ${start * 100}% to ${end * 100}% - Original: ${width}x$height, Crop: ${width}x$cropHeight at (0,$top)")

            // Create a new bitmap from the computed region
            return Bitmap.createBitmap(bitmap, 0, top, width, cropHeight)
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