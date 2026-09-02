package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import java.util.Calendar

/**
 * Date-of-birth/phone/email validation and input helpers, ported from the companion web app's
 * utils/dobUtils.js and utils/phoneUtils.js so the Android forms enforce/format the same way.
 */
object ValidationUtils {

    const val MINIMUM_AGE = 21
    private const val DEFAULT_COUNTRY_CODE = "1"
    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /**
     * Day/month/year spinner options, each with a "DD"/"MM"/"YYYY" prompt at index 0 (mirrors the
     * web app's disabled placeholder <option>). Picking day/month/year from full lists like this -
     * same as the web app's <select> dropdowns - means jumping to, say, 1980 is a couple of scrolls
     * or a tap-to-type, not dozens of "previous month" taps on a calendar widget.
     */
    val DAY_OPTIONS: List<String> = listOf("DD") + (1..31).map { it.toString().padStart(2, '0') }
    val MONTH_OPTIONS: List<String> = listOf("MM") + (1..12).map { it.toString().padStart(2, '0') }
    val YEAR_OPTIONS: List<String> by lazy {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        listOf("YYYY") + (0..99).map { (currentYear - it).toString() }
    }

    /** Whole-years age as of today, accounting for whether the birthday has happened yet this year. */
    fun calculateAge(day: Int, month: Int, year: Int): Int {
        val today = Calendar.getInstance()
        val todayMonth = today.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based; day/month here are 1-based.
        var age = today.get(Calendar.YEAR) - year
        val hadBirthdayThisYear = todayMonth > month ||
            (todayMonth == month && today.get(Calendar.DAY_OF_MONTH) >= day)
        if (!hadBirthdayThisYear) age -= 1
        return age
    }

    /**
     * Formats phone input as the user types. If they've typed a leading "+", treat it as an
     * explicit international number and just keep the digits. Otherwise, assume a US number and
     * format it as (XXX) XXX-XXXX as they type.
     */
    fun formatPhoneInput(raw: String): String {
        val hasPlus = raw.trim().startsWith("+")
        val digits = raw.filter { it.isDigit() }
        if (hasPlus) return "+$digits"

        val trimmed = digits.take(10)
        return when {
            trimmed.isEmpty() -> ""
            trimmed.length < 4 -> "($trimmed"
            trimmed.length < 7 -> "(${trimmed.substring(0, 3)}) ${trimmed.substring(3)}"
            else -> "(${trimmed.substring(0, 3)}) ${trimmed.substring(3, 6)}-${trimmed.substring(6)}"
        }
    }

    /** Normalizes to E.164, defaulting to +1 when no country code was given. */
    fun normalizePhone(value: String): String {
        val hasPlus = value.trim().startsWith("+")
        val digits = value.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else "+$DEFAULT_COUNTRY_CODE$digits"
    }

    /** +1 numbers get a strict 10-digit check; other country codes get a looser E.164-shaped check. */
    fun isValidPhone(value: String): Boolean {
        if (value.isBlank()) return false
        val normalized = normalizePhone(value)
        return if (normalized.startsWith("+1")) {
            Regex("^\\+1\\d{10}$").matches(normalized)
        } else {
            Regex("^\\+[1-9]\\d{7,14}$").matches(normalized)
        }
    }

    fun isValidEmail(value: String): Boolean = EMAIL_PATTERN.matches(value.trim())
}
