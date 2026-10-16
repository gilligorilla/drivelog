package com.example.drivelog.data

/**
 * Engineering-log inches: whole inches 0-11 and an eighth-inch fraction, written "whole-fraction",
 * e.g. "8-3/4", "0-1/8", "11-1/2". A zero fraction is written "8-0" so every value has the same
 * shape and is easy to parse in a spreadsheet.
 */
object InchesFormat {
    const val MAX_WHOLE = 11

    /** Fraction wheel entries, indexed 0..7 (eighths of an inch), lowest terms. */
    val FRACTIONS = listOf("0", "1/8", "1/4", "3/8", "1/2", "5/8", "3/4", "7/8")

    val WHOLES: List<Int> = (0..MAX_WHOLE).toList()

    fun format(whole: Int, fractionIndex: Int): String {
        require(whole in 0..MAX_WHOLE) { "whole inches must be 0..$MAX_WHOLE" }
        require(fractionIndex in FRACTIONS.indices) { "fraction index out of range" }
        return "$whole-${FRACTIONS[fractionIndex]}"
    }

    private val PATTERN = Regex("""(\d{1,2})-(0|1/8|1/4|3/8|1/2|5/8|3/4|7/8)""")

    /** True if [text] is exactly one valid engineering-format value. */
    fun isValid(text: String): Boolean {
        val m = PATTERN.matchEntire(text) ?: return false
        return m.groupValues[1].toInt() <= MAX_WHOLE
    }
}
