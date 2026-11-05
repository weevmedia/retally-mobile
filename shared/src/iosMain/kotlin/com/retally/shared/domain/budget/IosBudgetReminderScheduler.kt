/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.budget

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettings
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock

/**
 * iOS side of the daily budget reminders: one pending local notification per planned day.
 * iOS delivers them even if the app is closed, so nothing has to run in the background.
 */
class IosBudgetReminderScheduler : BudgetReminderScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun replaceAll(reminders: List<BudgetReminder>) {
        // Ids are per day, so cancelling every id the plan could use clears the old plan.
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        center.removePendingNotificationRequestsWithIdentifiers(budgetReminderIdsFrom(today))
        if (reminders.isEmpty()) return
        center.getNotificationSettingsWithCompletionHandler { settings: UNNotificationSettings? ->
            if (settings?.authorizationStatus == UNAuthorizationStatusNotDetermined) {
                // First time the budget goes orange / red: ask, then schedule if allowed.
                center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
                    if (granted) add(reminders)
                }
            } else {
                add(reminders) // silently dropped by iOS when notifications are off
            }
        }
    }

    private fun add(reminders: List<BudgetReminder>) {
        val nowSeconds = NSDate().timeIntervalSince1970
        reminders.forEach { reminder ->
            val seconds = reminder.triggerAtEpochMillis / 1000.0 - nowSeconds
            if (seconds < 1) return@forEach
            val content = UNMutableNotificationContent().apply {
                setTitle(reminder.title)
                setBody(reminder.body)
                setThreadIdentifier(THREAD_ID) // grouped together in Notification Center
                setSound(UNNotificationSound.defaultSound) // system notification sound
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(reminder.id, content, trigger),
                withCompletionHandler = null,
            )
        }
    }

    private companion object {
        const val THREAD_ID = "budget-reminders"
    }
}
