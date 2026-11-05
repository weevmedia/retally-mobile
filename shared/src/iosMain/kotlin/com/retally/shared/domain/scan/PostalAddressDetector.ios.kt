/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.scan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDataDetector
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.NSTextCheckingCityKey
import platform.Foundation.NSTextCheckingResult
import platform.Foundation.NSTextCheckingStateKey
import platform.Foundation.NSTextCheckingStreetKey
import platform.Foundation.NSTextCheckingTypeAddress
import platform.Foundation.NSTextCheckingZIPKey
import platform.Foundation.addressComponents
import platform.Foundation.create
import platform.Foundation.matchesInString

/**
 * iOS `NSDataDetector` with the address type: the same detector Mail and Messages use
 * to make addresses tappable. It hands back the components directly, so no splitting
 * is needed. Picks the match with the most components when the header contains several.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun detectPostalAddress(text: String): ParsedAddress? = withContext(Dispatchers.Default) {
    runCatching<ParsedAddress?> {
        val detector: NSDataDetector = NSDataDetector.dataDetectorWithTypes(NSTextCheckingTypeAddress, null)
            ?: return@runCatching null
        val nsText = NSString.create(string = text)
        val matches: List<*> = detector.matchesInString(text, options = 0uL, range = NSMakeRange(0uL, nsText.length))
        val found = mutableListOf<ParsedAddress>()
        for (match in matches) {
            val components: Map<Any?, *> = (match as? NSTextCheckingResult)?.addressComponents ?: continue
            found += ParsedAddress(
                street = components[NSTextCheckingStreetKey] as? String ?: "",
                city = components[NSTextCheckingCityKey] as? String ?: "",
                state = components[NSTextCheckingStateKey] as? String ?: "",
                zip = components[NSTextCheckingZIPKey] as? String ?: "",
            )
        }
        found.maxByOrNull { listOf(it.street, it.city, it.state, it.zip).count { part -> part.isNotEmpty() } }
    }.getOrNull()
}
