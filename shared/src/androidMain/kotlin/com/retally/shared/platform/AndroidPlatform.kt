package com.retally.shared.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.retally.shared.core.Platform

class AndroidPlatform(
    private val context: Context,
    override val appVersion: String,
) : Platform {
    override val name: String = "android"

    override val deviceModel: String = run {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        // Some models already include the maker ("Nokia X20"); others don't ("Pixel 8").
        if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }

    override val osVersion: String = Build.VERSION.RELEASE.orEmpty()

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
