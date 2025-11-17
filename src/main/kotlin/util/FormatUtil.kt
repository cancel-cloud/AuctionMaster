package util

import java.text.DecimalFormat

/**
 * Utility functions for formatting
 */
object FormatUtil {
    private val moneyFormat = DecimalFormat("#,##0.00")
    private val numberFormat = DecimalFormat("#,###")

    /**
     * Format money amount
     */
    fun formatMoney(amount: Double): String {
        return "$${moneyFormat.format(amount)}"
    }

    /**
     * Format a number
     */
    fun formatNumber(number: Int): String {
        return numberFormat.format(number)
    }

    /**
     * Format a number
     */
    fun formatNumber(number: Long): String {
        return numberFormat.format(number)
    }

    /**
     * Parse money string (handles $, commas, etc.)
     */
    fun parseMoney(moneyStr: String): Double? {
        return moneyStr.replace("$", "")
            .replace(",", "")
            .toDoubleOrNull()
    }
}
