/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.retally.app.R
import com.retally.shared.domain.notifications.NotificationInboxRepository
import com.retally.shared.domain.location.nearbyLog
import org.koin.core.context.GlobalContext

/** Local notification shown when the user enters a store geofence. */
object NearbyNotifications {
    // v2: high importance + default sound. A channel's sound/importance can't be changed
    // after creation, so the old silent-ish "nearby_stores" channel is deleted below.
    private const val CHANNEL_ID = "nearby_stores_v2"
    private const val OLD_CHANNEL_ID = "nearby_stores"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.settings_section_notifications),
                NotificationManager.IMPORTANCE_HIGH, // pops up as a heads-up banner
            ).apply {
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

    /** Intent extra: tapping the notification opens the Shopping List on this store. */
    const val EXTRA_SHOPPING_STORE = "com.retally.app.extra.SHOPPING_STORE"

    /**
     * @param listSummary items from the cached AI shopping list ("3 due: Banana, Milk…");
     * null keeps the generic "check your list" copy.
     */
    fun show(context: Context, storeName: String, listSummary: String? = null) {
        ensureChannel(context)
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.putExtra(EXTRA_SHOPPING_STORE, storeName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context,
                storeName.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val title = context.getString(R.string.nearby_notify_title, storeName)
        val body = listSummary ?: context.getString(R.string.nearby_notify_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pin)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(listSummary?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            // Pre-Android 8 (no channels): sound + heads-up come from these two.
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        nearbyLog("android: posting alert for \"$storeName\" (system notifications ${if (enabled) "on" else "OFF — bell only"})")
        // Unique id per alert: reusing the store's id just updated the old tray entry, so a
        // repeat alert didn't pop up again.
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        // Keep it in the Home bell for 48 hours (or until dismissed), even if the system
        // notification is swiped away or notifications are blocked.
        runCatching {
            GlobalContext.get().get<NotificationInboxRepository>().recordNearbyStore(storeName, title, body)
        }
    }

}
