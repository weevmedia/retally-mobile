/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.receipt

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * ReTally keeps receipts in view for the last [RECEIPT_WINDOW_MONTHS] calendar months: this
 * month and the 11 before it (the same window as the Budget history). Cloud restore only
 * downloads receipts inside it, and the Receipts tab only lists receipts inside it.
 */
const val RECEIPT_WINDOW_MONTHS = 12

/** Start of the window: midnight on the 1st of the month [RECEIPT_WINDOW_MONTHS] − 1 months ago. */
fun receiptWindowStart(
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Instant {
    val today = now.toLocalDateTime(zone).date
    val first = LocalDate(today.year, today.month, 1).minus(RECEIPT_WINDOW_MONTHS - 1, DateTimeUnit.MONTH)
    return first.atStartOfDayIn(zone)
}
