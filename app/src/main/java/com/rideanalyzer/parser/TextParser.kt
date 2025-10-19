package com.rideanalyzer.parser

import java.text.NumberFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.round

data class TripInfo(
    val fareARS: Double?,
    val distanceKm: Double?,
    val minutes: Double?,
    val etaMinutes: Double?,
    val rating: Double?,
    val ratingCount: Int?,
    val rawText: String,
    val source: String? = null
)

object TextParser {
    private val ARS_REGEX = Regex("(?i)ARS\\s*([\\d\\.,]+)|\\$\\s*([\\d\\.,]+)|([\\d\\.,]+)\\s*ARS")
    private val KM_REGEX = Regex("(\\d+(?:[.,]\\d+)?)\\s*(km|kms)\\b", RegexOption.IGNORE_CASE)
    private val MIN_REGEX = Regex("(\\d+(?:[.,]\\d+)?)\\s*(min|mins|m\\b)", RegexOption.IGNORE_CASE)
    private val RATING_REGEX = Regex("(\\d+\\.\\d+)\\s*\\(?\\s*(\\d{1,4})?\\s*\\)?") // e.g. 4.94 (524)

    private fun normalizeNumber(raw: String): Double? {
        var s = raw.replace("\\s".toRegex(), "")
        val hasDot = s.contains(".")
        val hasComma = s.contains(",")
        if (hasDot && hasComma) {
            // assume format 1.234,56 -> dot thousands, comma decimal
            if (s.indexOf('.') < s.indexOf(',')) {
                s = s.replace(".", "").replace(",", ".")
            } else {
                s = s.replace(",", "")
            }
        } else if (hasComma && !hasDot) {
            val parts = s.split(',')
            s = if (parts.size == 2 && parts[1].length == 3) s.replace(",", "") else s.replace(",", ".")
        } else if (hasDot && !hasComma) {
            val parts = s.split('.')
            if (parts.size == 2 && parts[1].length == 3) s = s.replace(".", "")
        }
        return s.toDoubleOrNull()
    }

    fun parse(text: String, source: String? = null): TripInfo {
        val raw = text.trim()
        var fare: Double? = null
        var km: Double? = null
        var minutes: Double? = null
        var eta: Double? = null
        var rating: Double? = null
        var ratingCount: Int? = null

        // Fare
        val mFare = ARS_REGEX.find(raw)
        if (mFare != null) {
            val v = mFare.groups[1]?.value ?: mFare.groups[2]?.value ?: mFare.groups[3]?.value
            fare = v?.let { normalizeNumber(it) }
        }

        // km - first occurrence
        val mKm = KM_REGEX.find(raw)
        if (mKm != null) {
            km = normalizeNumber(mKm.groupValues[1])
        }

        // minutes - we prefer the longest minutes found (could be ETA or trip minutes)
        val allMins = MIN_REGEX.findAll(raw).map { it.groupValues[1] }.toList()
        if (allMins.isNotEmpty()) {
            // heuristics: choose largest value as trip length, smallest as ETA
            val nums = allMins.mapNotNull { normalizeNumber(it) }
            if (nums.isNotEmpty()) {
                minutes = nums.maxOrNull()
                eta = nums.minOrNull()
            }
        }

        // rating
        val r = RATING_REGEX.find(raw)
        if (r != null) {
            rating = r.groups[1]?.value?.toDoubleOrNull()
            if (r.groups[2]?.value != null) {
                ratingCount = r.groups[2]?.value?.toIntOrNull()
            }
        }

        return TripInfo(
            fareARS = fare,
            distanceKm = km,
            minutes = minutes,
            etaMinutes = eta,
            rating = rating,
            ratingCount = ratingCount,
            rawText = raw,
            source = source
        )
    }
}
