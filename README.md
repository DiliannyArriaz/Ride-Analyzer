# Ride Analyzer App

## Overview

Ride Analyzer is an Android app designed to help ride-share drivers analyze trip offers from Uber and DiDi to determine if they are profitable based on configurable parameters.

## Problem & Solution

### The Issue
Android's security features prevent screen capture apps from reading text content from sensitive apps like Uber and DiDi. This is why the ML Kit text recognition was returning empty text results in your logs.

### The Solution
We've implemented an Accessibility Service approach which is more reliable for accessing UI content from other apps. This method works by:

1. Using Android's Accessibility Service API to monitor UI changes in ride apps
2. Extracting trip information directly from UI elements rather than screen capture
3. Providing real-time profitability analysis based on configurable parameters

## How to Use

### 1. Grant Required Permissions

1. Launch the app
2. Grant overlay permission when prompted (required for showing trip analysis)
3. Grant other runtime permissions (camera, audio, etc.)
4. Allow battery optimization exemption for better performance

### 2. Enable Accessibility Service

1. Tap "Start Accessibility Service" button
2. You'll be directed to Android's Accessibility Settings
3. Find "Ride Analyzer" in the list and enable it
4. The service will now monitor Uber and DiDi apps

### 3. Using the App

1. Open Uber or DiDi driver app
2. When a trip offer appears, the app will automatically:
   - Extract trip details (price, distance, time)
   - Calculate profitability based on your settings
   - Show an overlay with the analysis results

### 4. Configuring Profitability Parameters

You can adjust these parameters in the app settings:
- Desired Hourly Rate (ARS)
- Cost Per Kilometer (ARS)
- Minimum Rating (optional)

## Technical Implementation

### Accessibility Service Approach

The app uses `RideAccessibilityService` which:

1. Monitors UI changes in Uber and DiDi apps
2. Traverses the UI hierarchy to find relevant text elements
3. Extracts trip information using regex patterns
4. Calculates profitability and shows overlay

### Screen Capture Approach (Fallback)

The screen capture service is still available but less reliable due to Android security restrictions:

1. Captures screenshots at regular intervals
2. Attempts to recognize text with ML Kit
3. Processes recognized text to extract trip info

## Testing and Validation

### How to verify bottom-half cropping works:

1. Enable DEBUG mode in the app
2. Use the screen capture service (not accessibility service)
3. Check for saved images in `Android/data/com.rideanalyzer.app/files/debug_ocr/`
4. Verify that only the bottom half of the screen is saved in the images

### Commands to monitor OCR processing:

```bash
adb logcat -s ScreenshotService OCRProcessor ImagePreprocessor
```

Look for these specific log messages:
- `Region bottom-half hash=` - Shows the hash of the cropped region
- `Skipping OCR (black)` - Indicates FLAG_SECURE detection
- `OCR attempt start` - Shows when OCR is actually attempted
- `OCR result length=` - Shows the length of recognized text

## Troubleshooting

### If the overlay doesn't appear:

1. Ensure Accessibility Service is enabled
2. Check that you're using Uber or DiDi driver apps (not passenger apps)
3. Make sure trip offers are actually visible on screen
4. Verify all permissions are granted

### If profitability calculations seem incorrect:

1. Check your configured parameters in Settings
2. Verify that trip information is being extracted correctly
3. Look at the logs for debugging information

## Limitations

1. Accessibility Service only works with specific app packages
2. UI changes in Uber/DiDi apps may require pattern updates
3. Some devices may have additional restrictions on accessibility services

## Future Improvements

1. Support for additional ride-share platforms
2. Enhanced UI element detection for different app versions
3. Improved profitability calculation algorithms
4. Better handling of different currencies and regions

## Logs and Debugging

Enable USB debugging and use `adb logcat` to view detailed logs:
```
adb logcat -s RideAccessibilityService TripAnalyzer ScreenshotService
```

Look for these key log tags:
- `RideAccessibilityService` - Accessibility service operations
- `TripAnalyzer` - Trip information processing and profitability calculations
- `ScreenshotService` - Screen capture operations (if using that approach)
- `ImagePreprocessor` - Image cropping and preprocessing
- `TextRecognizer` - OCR processing