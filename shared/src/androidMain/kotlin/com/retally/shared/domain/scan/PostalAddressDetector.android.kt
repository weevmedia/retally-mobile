/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.scan

import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import com.retally.shared.core.Platform
import com.retally.shared.platform.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/**
 * Android's system `TextClassifier`: on-device address linkification, the same engine
 * that underlines addresses in text selection. It only reports the address span, so
 * [AddressExtractor] splits it into parts. Must not run on the main thread. The explicit
 * entity list needs API 29, so older devices return null and the regex path is used.
 */
actual suspend fun detectPostalAddress(text: String): ParsedAddress? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val platform = runCatching { KoinPlatform.getKoin().get<Platform>() as? AndroidPlatform }.getOrNull() ?: return null
    return withContext(Dispatchers.Default) {
        runCatching {
            val manager = platform.appContext().getSystemService(TextClassificationManager::class.java)
                ?: return@runCatching null
            val request = TextLinks.Request.Builder(text)
                .setEntityConfig(
                    TextClassifier.EntityConfig.createWithExplicitEntityList(listOf(TextClassifier.TYPE_ADDRESS)),
                )
                .build()
            val links = manager.textClassifier.generateLinks(request).links
            links
                .filter { it.entityCount > 0 && it.getEntity(0) == TextClassifier.TYPE_ADDRESS }
                .map { text.substring(it.start, it.end) }
                // The longest span is the full street + city + ZIP; shorter ones are fragments.
                .maxByOrNull { it.length }
                ?.let { AddressExtractor.extract(it) }
        }.getOrNull()
    }
}
