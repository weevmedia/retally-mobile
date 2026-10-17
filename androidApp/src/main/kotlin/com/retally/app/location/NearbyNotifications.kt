package com.retally.app.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.retally.app.R

/** Local notification shown when the user enters a store geofence. */
object NearbyNotifications {
    private const val CHANNEL_ID = "nearby_stores"
    private const val DEBUG_NOTIFICATION_ID = 9001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.settings_section_notifications),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pin)
            .setContentTitle(context.getString(R.string.nearby_notify_title, storeName))
            .setContentText(listSummary ?: context.getString(R.string.nearby_notify_body))
            .setStyle(listSummary?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(storeName.hashCode(), notification)
    }

    /** TEST-LOCATION-PING: debug only — remove before release. */
    fun showDebug(context: Context, title: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pin)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(DEBUG_NOTIFICATION_ID, notification)
    }
}
