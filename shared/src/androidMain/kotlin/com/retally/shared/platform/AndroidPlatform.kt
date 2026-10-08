package com.retally.shared.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.retally.shared.core.Platform

class AndroidPlatform(
    private val context: Context,
    override val appVersion: String,
) : Platform {
    override val name: String = "android"

    /** Shared-module access to the application context (geocoder, notifications). */
    internal fun appContext(): Context = context

    override fun openUrl(url: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
