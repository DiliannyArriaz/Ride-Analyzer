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
    // Detecta dos formatos distintos:
    // 1. Formato Didi: "$3.129,10" (precio real del viaje)
    // 2. Formato Uber: "ARS4518" (sin espacios, precio del viaje)
    private val ARS_REGEX = Regex("(?i)(?:ARS([\\d]+))|(?:\\$\\s*([\\d.,]+))")
    
    // Soporta formatos: "2.9km", "(2.9km)", "2,9 km", "2.9 kms"
    private val KM_REGEX = Regex("\\(?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:km|kms)\\s*\\)?", RegexOption.IGNORE_CASE)
    
    // Soporta formatos: "9min", "9 min", "(9min)", "9m"
    private val MIN_REGEX = Regex("\\(?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:min|mins|m)\\b\\s*\\)?", RegexOption.IGNORE_CASE)
    
    // Soporta formatos: 4.94 (524), 4,94 (524)
    private val RATING_REGEX = Regex("(\\d+[.,]\\d+)\\s*\\(?\\s*(\\d{1,4})?\\s*\\)?") // e.g. 4.94 (524)

    private fun normalizeNumber(raw: String, isUberDynamic: Boolean = false): Double? {
        var s = raw.replace("\\s".toRegex(), "")
        
        // Si es dinámica de Uber, solo necesitamos el número entero
        if (isUberDynamic) {
            return s.toDoubleOrNull()
        }
        
        val hasDot = s.contains(".")
        val hasComma = s.contains(",")
        
        // Limpiamos cualquier símbolo de moneda
        s = s.replace("[$]".toRegex(), "")
        
        if (hasDot && hasComma) {
            // Para formato Didi: $3.129,10 -> 3129.10
            if (s.indexOf('.') < s.indexOf(',')) {
                s = s.replace(".", "").replace(",", ".")
            } else {
                s = s.replace(",", "")
            }
        } else if (hasComma && !hasDot) {
            val parts = s.split(',')
            if (parts.size == 2) {
                // Para Didi: asumimos coma decimal si tiene 2 dígitos después
                s = if (parts[1].length <= 2) {
                    s.replace(",", ".")  // 3,10 -> 3.10
                } else {
                    s.replace(",", "")   // 3,129 -> 3129
                }
            }
        }
        
        return try {
            s.toDouble()
        } catch (e: NumberFormatException) {
            null
        }
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
            // Si encontramos un grupo en el formato ARS, es dinámica de Uber
            val isUberDynamic = mFare.groups[1]?.value != null
            val v = mFare.groups[1]?.value ?: mFare.groups[2]?.value
            fare = v?.let { normalizeNumber(it, isUberDynamic) }
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
