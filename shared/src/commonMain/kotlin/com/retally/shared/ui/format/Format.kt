/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.format

import com.retally.shared.core.locale.Region
import kotlin.time.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Currency string for an amount in [currencyCode] — "$12.30", "$1,234.56" — using the
 * symbol, placement and separators of the [Region] that currency belongs to. A code no
 * launch market uses falls back to US conventions with the code itself as the symbol
 * ("EUR 12.30"), so nothing is ever shown as dollars that isn't. Pure Kotlin (no platform
 * `NumberFormat`) so the two platforms stay pixel-identical.
 */
fun formatCurrency(amount: Double, currencyCode: String = Region.US.currencyCode): String {
    val region = Region.forCurrency(currencyCode)
    val cents = (abs(amount) * 100).roundToLong()
    val whole = groupThousands(cents / 100, region.groupingSeparator)
    val frac = (cents % 100).toString().padStart(2, '0')
    val number = "$whole${region.decimalSeparator}$frac"
    val sign = if (amount < 0) "-" else ""
    val symbol = if (region.currencyCode.equals(currencyCode, ignoreCase = true)) region.currencySymbol else "${currencyCode.uppercase()} "
    return if (region.symbolBefore) "$sign$symbol$number" else "$sign$number ${symbol.trim()}"
}

private fun groupThousands(n: Long, separator: Char): String =
    n.toString().reversed().chunked(3).joinToString(separator.toString()).reversed()

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

/** "3:04 PM" in the device time zone. */
fun formatTime(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = instant.toLocalDateTime(timeZone)
    val hour12 = when (val h = dt.hour % 12) { 0 -> 12; else -> h }
    val ampm = if (dt.hour < 12) "AM" else "PM"
    return "$hour12:${dt.minute.toString().padStart(2, '0')} $ampm"
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
