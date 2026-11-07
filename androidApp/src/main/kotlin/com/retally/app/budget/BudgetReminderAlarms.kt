/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.budget

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.retally.app.R
import com.retally.shared.domain.budget.BudgetReminder
import com.retally.shared.domain.budget.BudgetReminderScheduler
import com.retally.shared.domain.budget.BudgetReminderLog
import org.koin.core.context.GlobalContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android side of the daily budget reminders: one inexact AlarmManager alarm per planned day
 * (no exact-alarm permission needed; a few minutes' drift is fine for a reminder). The plan is
 * kept in SharedPreferences so it can be cancelled later and re-armed after a reboot.
 */
class AndroidBudgetReminderScheduler(context: Context) : BudgetReminderScheduler {
    private val appContext = context.applicationContext

    override fun replaceAll(reminders: List<BudgetReminder>) {
        val prefs = prefs(appContext)
        // Cancel what the previous plan armed.
        decode(prefs.getString(KEY_PLAN, null)).forEach { cancel(appContext, it) }
        prefs.edit().putString(KEY_PLAN, encode(reminders)).apply()
        arm(appContext, reminders)
    }

    companion object {
        private const val PREFS = "budget_reminders"
        private const val KEY_PLAN = "plan.v1"
        internal const val EXTRA_TITLE = "title"
        internal const val EXTRA_BODY = "body"
        internal const val EXTRA_ID = "id"

        /** Alarms don't survive a reboot: re-arm whatever is still in the future. */
        fun rearm(context: Context) {
            val now = System.currentTimeMillis()
            arm(context, decode(prefs(context).getString(KEY_PLAN, null)).filter { it.triggerAtEpochMillis > now })
        }

        private fun arm(context: Context, reminders: List<BudgetReminder>) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            reminders.forEach { reminder ->
                alarms.set(AlarmManager.RTC_WAKEUP, reminder.triggerAtEpochMillis, pendingIntent(context, reminder))
            }
        }

        private fun cancel(context: Context, reminder: BudgetReminder) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, reminder))
        }

        private fun pendingIntent(context: Context, reminder: BudgetReminder): PendingIntent {
            val intent = Intent(context, BudgetReminderReceiver::class.java)
                .putExtra(EXTRA_ID, reminder.id)
                .putExtra(EXTRA_TITLE, reminder.title)
                .putExtra(EXTRA_BODY, reminder.body)
            // Request code = the day, so each day's alarm is distinct and replaceable.
            return PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun encode(reminders: List<BudgetReminder>): String = JSONArray().apply {
            reminders.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("at", it.triggerAtEpochMillis)
                        .put("title", it.title)
                        .put("body", it.body),
                )
            }
        }.toString()

        private fun decode(raw: String?): List<BudgetReminder> = runCatching {
            val array = JSONArray(raw ?: return emptyList())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                BudgetReminder(o.getString("id"), o.getLong("at"), o.getString("title"), o.getString("body"))
            }
        }.getOrDefault(emptyList())
    }
}

/** Fires at the planned time and posts the budget reminder. */
class BudgetReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AndroidBudgetReminderScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(AndroidBudgetReminderScheduler.EXTRA_BODY) ?: return
        val id = intent.getStringExtra(AndroidBudgetReminderScheduler.EXTRA_ID) ?: title
        // The bell keeps it for 48 hours even when system notifications are off.
        recordInBell()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(context, id.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pin)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        // One id for all budget reminders: a new day's reminder replaces yesterday's.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /** Files the reminder under the Home bell right away (the app also does this on open). */
    private fun recordInBell() {
        runCatching { GlobalContext.get().get<BudgetReminderLog>().collectFired() }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.budget_reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

    private companion object {
        // v2: high importance + default sound (channel sound can't change after creation).
        const val CHANNEL_ID = "budget_reminders_v2"
        const val OLD_CHANNEL_ID = "budget_reminders"
        const val NOTIFICATION_ID = 7301
    }
}
