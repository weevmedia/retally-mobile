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

android {
    namespace = "com.retally.app"
    compileSdk = Versions.androidCompileSdk

    defaultConfig {
        applicationId = "com.weevmedia.retally"
        minSdk = Versions.androidMinSdk
        targetSdk = Versions.androidTargetSdk
        versionCode = 1
        versionName = "1.0.0"

        // AdMob App ID (the one with the "~"), from apps.admob.com → Apps → ReTally (Android).
        // The real id is fine in every build type: which *ad units* a build requests decides
        // whether real or test creatives are served (see HOME_BANNER_UNIT_ID below).
        manifestPlaceholders["admobAppId"] = AdMob.androidAppId

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
            signingConfig = if (keystoreProperties.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation(Deps.Android.playServicesAds)
    implementation(Deps.Android.playServicesLocation)
    implementation(Deps.Android.userMessagingPlatform)

    // Receipt scanning (ML Kit document scanner UI + on-device text recognition).
    implementation(Deps.Android.mlkitDocumentScanner)
    implementation(Deps.Android.mlkitTextRecognition)
    implementation(Deps.Kotlinx.coroutinesPlayServices)
    // Custom scanner UI: CameraX live preview (replaces the ML Kit scanner UI).
    implementation(Deps.Android.cameraXCore)
    implementation(Deps.Android.cameraXCamera2)
    implementation(Deps.Android.cameraXLifecycle)
    implementation(Deps.Android.cameraXView)
    implementation(Deps.Android.guava)
    // Material icons for the scanner UI (close, flash, gallery buttons).
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}
