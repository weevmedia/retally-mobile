package com.retally.shared.ui.format

import kotlin.time.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * USD-style currency string ("$12.30"). Locale-aware formatting is a platform concern the
 * scanner work will bring in with the receipt's own currency; for the shell a fixed format
 * keeps the two platforms pixel-identical.
 */
fun formatCurrency(amount: Double): String {
    val cents = (abs(amount) * 100).roundToLong()
    val whole = cents / 100
    val frac = cents % 100
    val sign = if (amount < 0) "-" else ""
    return "$sign$${groupThousands(whole)}.${frac.toString().padStart(2, '0')}"
}

private fun groupThousands(n: Long): String =
    n.toString().reversed().chunked(3).joinToString(",").reversed()

/** "Sep 12, 2026" in the device time zone. */
fun formatShortDate(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = instant.toLocalDateTime(timeZone).date
    return "${date.month.shortName()} ${date.day}, ${date.year}"
}

/** "Sep 12, 2026 · 3:04 PM". */
fun formatDateTime(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = instant.toLocalDateTime(timeZone)
    val hour12 = when (val h = dt.hour % 12) { 0 -> 12; else -> h }
    val ampm = if (dt.hour < 12) "AM" else "PM"
    return "${formatShortDate(instant, timeZone)} · $hour12:${dt.minute.toString().padStart(2, '0')} $ampm"
}

private fun Month.shortName(): String = name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

/** "Sep" — the current month, for the "Spent in Sep" tile. */
fun formatMonthShort(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    instant.toLocalDateTime(timeZone).date.month.shortName()

/** "Sep 2026" — month filter chips and section headers on the Receipts tab. */
fun formatMonthYearShort(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = instant.toLocalDateTime(timeZone).date
    return "${date.month.shortName()} ${date.year}"
}

/** "September 2026" — section label for grouping receipts by month. */
fun formatMonthYear(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = instant.toLocalDateTime(timeZone).date
    return "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
}
