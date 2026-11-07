/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose") version Versions.kotlin
    // Typed navigation arguments (`SplashFragmentDirections.actionSplashToHome(...)`).
    id("androidx.navigation.safeargs.kotlin")
    // Reads androidApp/google-services.json (Firebase console → Project settings → Your apps).
    id("com.google.gms.google-services")
    // Uploads the R8 mapping file on release builds so Crashlytics stack traces are readable.
    id("com.google.firebase.crashlytics")
}

/**
 * AdMob identifiers. Paste your own from https://apps.admob.com:
 *  - App ID     → Apps → ReTally (Android) → App settings   (format ca-app-pub-XXXX~YYYY)
 *  - Ad unit ID → Apps → ReTally (Android) → Ad units       (format ca-app-pub-XXXX/YYYY)
 * Until replaced, both fall back to Google's public sample ids, which serve test creatives.
 */
object AdMob {
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"

    val androidAppId: String = "ca-app-pub-3023631555197048~3442158733".takeIf { it.startsWith("ca-app-pub-") } ?: TEST_APP_ID
    val androidHomeBannerUnitId: String = "ca-app-pub-3023631555197048/4514375284".takeIf { it.startsWith("ca-app-pub-") } ?: TEST_BANNER_UNIT
}

// Release signing is read from `keystore.properties` at the repo root (gitignored):
//   storeFile=signing/retally-release.jks
//   storePassword=...
//   keyAlias=retally
//   keyPassword=...
// Without the file, release builds fall back to the debug key so CI can still assemble.
val keystoreProperties = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) {
    keystoreProperties.load(FileInputStream(keystoreFile))
}

// App version lives in version.properties (shared with iOS via iosApp/Version.xcconfig).
// CI can pin the code without touching source: ./gradlew ... -Pretally.versionCode=123
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = appVersion.getProperty("versionName").trim()
val appVersionCode: Int = (findProperty("retally.versionCode") as String?)?.toInt()
    ?: appVersion.getProperty("versionCode").trim().toInt()

// Keep iosApp/Version.xcconfig in step with version.properties. Wired into preBuild so an
// Android build can never ship a version iOS doesn't also see.
val syncIosVersion by tasks.registering {
    description = "Writes iosApp/Version.xcconfig from version.properties"
    val source = rootProject.file("version.properties")
    val target = rootProject.file("iosApp/Version.xcconfig")
    // Plain values only, so the task is configuration-cache friendly.
    val marketingVersion = appVersionName
    val projectVersion = appVersion.getProperty("versionCode").trim()
    inputs.file(source)
    outputs.file(target)
    doLast {
        target.writeText(
            """
            |// GENERATED from version.properties by `./gradlew syncIosVersion` — do not edit here.
            |MARKETING_VERSION = $marketingVersion
            |CURRENT_PROJECT_VERSION = $projectVersion
            |""".trimMargin(),
        )
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(syncIosVersion) }

// Release builds need the upload key. Checked when a release task actually runs (not at
// configuration), so debug builds and IDE sync work on machines without keystore.properties.
val hasReleaseKeystore = keystoreProperties.isNotEmpty()
val requireReleaseKeystore by tasks.registering {
    doLast {
        if (!hasReleaseKeystore) {
            throw GradleException(
                "Release build needs the upload key: create keystore.properties in the project root " +
                    "(storeFile, storePassword, keyAlias, keyPassword). Refusing to sign a release with the debug key.",
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(requireReleaseKeystore) }

android {
    namespace = "com.retally.app"
    compileSdk = Versions.androidCompileSdk

    defaultConfig {
        applicationId = "com.weevmedia.retally"
        minSdk = Versions.androidMinSdk
        targetSdk = Versions.androidTargetSdk
        versionCode = appVersionCode
        versionName = appVersionName

        // AdMob App ID (the one with the "~"), from apps.admob.com → Apps → ReTally (Android).
        // The real id is fine in every build type: which *ad units* a build requests decides
        // whether real or test creatives are served (see HOME_BANNER_UNIT_ID below).
        manifestPlaceholders["admobAppId"] = AdMob.androidAppId

        // OpenCV ships native code for every CPU type; keep phones (64/32-bit ARM) and the
        // x86_64 emulator only. Play still splits the download per phone.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        // Backend for cloud backup. Empty = feature stays in placeholder mode.
        buildConfigField("String", "CLOUD_BASE_URL", "\"\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // NOTE: no versionNameSuffix — the More menu shows a clean version (e.g. 1.0.0),
            // never "1.0.0-debug".
            // Production unit in debug too (per request) — debug builds now request the real ad unit.
            // AndroidAdsInitializer registers the emulator and DEBUG_TEST_DEVICE_IDS as test
            // devices, so the real unit serves "Test Ad" creatives without counting as live
            // impressions.
            buildConfigField("String", "HOME_BANNER_UNIT_ID", "\"${AdMob.androidHomeBannerUnitId}\"") 
        }
        release {
            buildConfigField("String", "HOME_BANNER_UNIT_ID", "\"${AdMob.androidHomeBannerUnitId}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Always the upload key. No debug-key fallback: a debug-signed "release" is rejected
            // by Play and fails App Check, so without keystore.properties the release build
            // stops (see requireReleaseKeystore above) instead of quietly signing with debug.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    // Screens are Fragments behind a Navigation graph; each hosts a ComposeView that renders
    // the shared stateless `*Screen` composables.
    with(Deps.AndroidX) {
        implementation(appCompat)
        implementation(fragmentKtx)
        implementation(navigationFragmentKtx)
        implementation(navigationUiKtx)
        implementation(lifecycleRuntimeKtx)
        implementation(activityCompose)
        implementation(splashScreen)
        implementation(material)
    }
    // Compose bits the fragments touch directly (ComposeView, SnackbarHostState, collectAsState).
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(Deps.Compose.Multiplatform.lifecycleRuntimeCompose)

    implementation(Deps.Koin.android)
    implementation(Deps.Firebase.crashlytics)
    // App Check: proves to the backend that calls come from this app (see ReTallyApplication).
    implementation(Deps.Firebase.appCheckPlayIntegrity)
    debugImplementation(Deps.Firebase.appCheckDebug)
    implementation(Deps.Android.playServicesAds)
    // Unity Ads mediation adapter (AdMob bidding for banners) + the Unity Ads SDK itself.
    // The adapter alone does not bring the Unity classes: without the SDK line, release
    // R8 fails on missing com.unity3d.ads.* classes (debug only failed at runtime instead).
    implementation("com.unity3d.ads:unity-ads:4.21.0")
    implementation("com.google.ads.mediation:unity:4.21.0.0")
    implementation(Deps.Android.playServicesLocation)
    implementation(Deps.Android.userMessagingPlatform)

    // Receipt scanning (ML Kit document scanner UI + on-device text recognition).
    implementation(Deps.Android.mlkitDocumentScanner)
    implementation(Deps.Android.mlkitTextRecognition)
    // Reads the barcode bars themselves (ReceiptTextRecognizer) — text OCR on the small
    // digits under a top-of-receipt barcode is what kept dropping Walmart/Shaw's codes.
    implementation(Deps.Android.mlkitBarcodeScanning)
    // Sign in with Google.
    implementation(Deps.Android.credentials)
    implementation(Deps.Android.credentialsPlayServices)
    implementation(Deps.Android.googleId)
    implementation(Deps.Kotlinx.coroutinesPlayServices)
    // Custom scanner UI: CameraX live preview (replaces the ML Kit scanner UI).
    implementation(Deps.Android.cameraXCore)
    implementation(Deps.Android.cameraXCamera2)
    implementation(Deps.Android.cameraXLifecycle)
    implementation(Deps.Android.cameraXView)
    implementation(Deps.Android.guava)
    // Finds the receipt in the photo and straightens it (ReceiptDocumentDetector).
    implementation(Deps.Android.openCv)
    // Material icons for the scanner UI (close, flash, gallery buttons).
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}
