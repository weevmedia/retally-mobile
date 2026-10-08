package com.retally.app.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.retally.app.BuildConfig
import com.retally.shared.domain.ads.AdsInitializer
import com.retally.shared.domain.ads.AdsReadiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Consent-first ads bootstrap:
 * 1. Ask UMP for the current consent status and show the form if required (GDPR/US states).
 * 2. Once ads may be requested, initialize the Mobile Ads SDK on a background thread.
 *
 * Failures never block the app: a consent/network failure resolves [AdsReadiness.UNAVAILABLE]
 * and the splash continues; banners simply stay hidden for this session.
 */
class AndroidAdsInitializer(private val application: Application) : AdsInitializer {

    private val initialized = AtomicBoolean(false)

    private companion object {
        const val TAG = "ReTallyAds"
        const val ACTIVITY_WAIT_ATTEMPTS = 20
        const val ACTIVITY_WAIT_STEP_MS = 50L
        /**
         * Hashed ids of physical dev phones. On the first ad request logcat prints
         * `Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("<hash>"))` —
         * paste that hash here. Emulators are test devices automatically.
         */
        val DEBUG_TEST_DEVICE_IDS: List<String> = emptyList()

        /**
         * Test ads need no consent. Until a UMP privacy message is published in the AdMob
         * console for the real app id, `canRequestAds()` stays false and no banner would ever
         * show; debug builds bypass that gate. Release builds always honour consent.
         */
        val ALLOW_WITHOUT_CONSENT: Boolean = BuildConfig.DEBUG
    }

    override suspend fun initialize(): AdsReadiness {
        // The splash dispatches Start from Fragment.onCreate, i.e. before the Activity has
        // resumed, so the tracker is usually still empty on the first call. Wait briefly for
        // onResume instead of giving up (which silently hid every banner for the session).
        val activity = awaitResumedActivity()

        val consent = UserMessagingPlatform.getConsentInformation(application)
        val canRequestAds = when {
            activity != null -> gatherConsent(activity, consent)
            else -> consent.canRequestAds() || ALLOW_WITHOUT_CONSENT
        }
        Log.d(TAG, "consent resolved: canRequestAds=$canRequestAds (activity=${activity != null}, debugBypass=$ALLOW_WITHOUT_CONSENT)")
        if (!canRequestAds) return AdsReadiness.UNAVAILABLE

        if (initialized.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) {
                if (BuildConfig.DEBUG) {
                    // Real unit ids + registered test devices: creatives come back labelled
                    // "Test Ad" and never count as live impressions.
                    MobileAds.setRequestConfiguration(
                        RequestConfiguration.Builder()
                            .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR) + DEBUG_TEST_DEVICE_IDS)
                            .build(),
                    )
                    Log.d(TAG, "test devices: emulator + ${DEBUG_TEST_DEVICE_IDS.size} phone(s); a physical phone logs its hash on the first request — add it to DEBUG_TEST_DEVICE_IDS")
                }
                suspendCancellableCoroutine { cont -> MobileAds.initialize(application) { cont.resume(Unit) } }
            }
        }
        Log.d(TAG, "Mobile Ads SDK ready")
        return AdsReadiness.READY
    }

    private suspend fun awaitResumedActivity(): Activity? {
        repeat(ACTIVITY_WAIT_ATTEMPTS) {
            CurrentActivityTracker.current?.let { return it }
            delay(ACTIVITY_WAIT_STEP_MS)
        }
        return CurrentActivityTracker.current
    }

    private suspend fun gatherConsent(activity: Activity, consent: ConsentInformation): Boolean =
        suspendCancellableCoroutine { cont ->
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()
            consent.requestConsentInfoUpdate(
                activity,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                        if (cont.isActive) cont.resume(consent.canRequestAds() || ALLOW_WITHOUT_CONSENT)
                    }
                },
                { _ ->
                    // Consent update failed (offline, misconfigured). Fall back to whatever the
                    // SDK already knows; typically false on a first launch, true afterwards.
                    // Debug builds use Google's TEST unit ids, which need no consent, so they
                    // keep going instead of hiding every banner for the session.
                    if (cont.isActive) cont.resume(consent.canRequestAds() || ALLOW_WITHOUT_CONSENT)
                },
            )
        }
}

/** Tracks the resumed Activity so the consent form has a host without leaking it. */
object CurrentActivityTracker : Application.ActivityLifecycleCallbacks {
    private var ref: WeakReference<Activity>? = null
    val current: Activity? get() = ref?.get()

    override fun onActivityResumed(activity: Activity) {
        ref = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
