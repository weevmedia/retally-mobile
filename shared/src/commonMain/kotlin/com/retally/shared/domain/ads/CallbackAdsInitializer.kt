package com.retally.shared.domain.ads

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Bridges a callback-style SDK bootstrap (Swift completion handler, Android listener) into the
 * suspending [AdsInitializer] contract. The completion may be invoked from any thread; the
 * continuation resumes exactly once even if the SDK calls back twice.
 */
class CallbackAdsInitializer(
    private val start: (onComplete: (ready: Boolean) -> Unit) -> Unit,
) : AdsInitializer {

    override suspend fun initialize(): AdsReadiness = suspendCancellableCoroutine { continuation ->
        var resumed = false
        start { ready ->
            if (!resumed && continuation.isActive) {
                resumed = true
                continuation.resume(if (ready) AdsReadiness.READY else AdsReadiness.UNAVAILABLE)
            }
        }
    }
}

/** For tests and for builds that ship without ads. */
object NoOpAdsInitializer : AdsInitializer {
    override suspend fun initialize(): AdsReadiness = AdsReadiness.UNAVAILABLE
}
