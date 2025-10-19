package com.rideanalyzer.app.model

/**
 * A data class representing all the information about a detected trip.
 * Using a data class automatically provides getters, setters, toString(), equals(), and hashCode().
 */
data class TripInfo(
    var platform: String? = null,
    var price: Double = 0.0,
    var currency: String = "ARS",
    var distance: Double = 0.0,
    var distanceUnit: String = "km",
    var estimatedMinutes: Int = 0,
    var rating: Double? = null,
    var isProfitable: Boolean = false,
    var pricePerKm: Double = 0.0,
    var pricePerMinute: Double = 0.0,
    var pricePerHour: Double = 0.0,
    val detectedAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if the essential information for a trip has been extracted.
     */
    fun isValid(): Boolean {
        return platform != null && price > 0 && distance > 0
    }
    
    /**
     * Provides a formatted string representation of the trip information.
     */
    override fun toString(): String {
        return "TripInfo(platform=$platform, price=$price $currency, distance=$distance $distanceUnit, estimatedMinutes=$estimatedMinutes, rating=$rating, isProfitable=$isProfitable)"
    }
}