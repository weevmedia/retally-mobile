// Single source of truth for versions and coordinates, same shape as the Sidearm build.
// Plugin classpath entries live in the root build.gradle.kts `buildscript` block; module
// build files reference `Deps.*` / `Versions.*` directly.

object Versions {
    const val androidMinSdk = 26
    const val androidCompileSdk = 35
    const val androidTargetSdk = androidCompileSdk

    // Toolchain. `kotlinVersion` in gradle.properties must match `kotlin`.
    const val kotlin = "2.2.20"
    const val agp = "8.10.1"
    const val composeMultiplatform = "1.9.3"
    // Icon set stopped shipping with newer CMP releases; 1.7.3 is API-compatible with 1.7+.
    const val composeMaterialIcons = "1.7.3"
    const val jetbrainsLifecycle = "2.9.1"

    const val kotlinCoroutines = "1.10.2"
    const val kotlinxSerialization = "1.9.0"
    // 0.7+: Instant/Clock live in kotlin.time; the domain layer uses those types directly.
    const val kotlinxDatetime = "0.7.1"
    const val koin = "4.1.0"
    const val ktor = "3.2.3"
    const val multiplatformSettings = "1.3.0"
    // SQLDelight 2.1.0 verified on Maven Central 2026-10-01; Kotlin 2.x era, KMP-ready.
    const val sqldelight = "2.1.0"

    const val activityCompose = "1.10.1"
    const val splashScreen = "1.0.1"
    const val appCompat = "1.7.0"
    const val fragment = "1.8.6"
    const val navigation = "2.8.9"
    const val material = "1.12.0"
    const val androidxLifecycle = "2.9.1"
    const val playServicesAds = "24.4.0"
    const val playServicesLocation = "21.4.0"
    const val userMessagingPlatform = "3.2.0"
    // Receipt scanning: Play-services-delivered ML Kit (no bundled model, small APK).
    const val mlkitDocumentScanner = "16.0.0-beta1"
    const val mlkitTextRecognition = "19.0.1"

    const val turbine = "1.2.1"

    // Firebase. GitLive wraps the native Android/iOS SDKs behind one Kotlin API; the Android
    // side pulls the Firebase BoM transitively, the iOS side links whatever the Xcode app adds
    // through SPM (see iosApp/README-firebase.md).
    const val gitliveFirebase = "2.5.0"
    const val googleServicesPlugin = "4.5.0"
}

object Deps {
    object Kotlinx {
        const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCoroutines}"
        const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinCoroutines}"
        const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinCoroutines}"
        const val coroutinesPlayServices = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:${Versions.kotlinCoroutines}"
        const val serializationJson = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}"
        const val dateTime = "org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}"
    }

    object Compose {
        // Compose Multiplatform artifacts by direct coordinates — the `compose.*` Gradle DSL
        // aliases are deprecated since CMP 1.10.
        object Multiplatform {
            const val runtime = "org.jetbrains.compose.runtime:runtime:${Versions.composeMultiplatform}"
            const val foundation = "org.jetbrains.compose.foundation:foundation:${Versions.composeMultiplatform}"
            const val ui = "org.jetbrains.compose.ui:ui:${Versions.composeMultiplatform}"
            const val componentsResources = "org.jetbrains.compose.components:components-resources:${Versions.composeMultiplatform}"
            const val materialIconsExtended = "org.jetbrains.compose.material:material-icons-extended:${Versions.composeMaterialIcons}"
            const val lifecycleRuntimeCompose = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:${Versions.jetbrainsLifecycle}"
            const val lifecycleViewModel = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:${Versions.jetbrainsLifecycle}"
        }
    }

    object AndroidX {
        const val activityCompose = "androidx.activity:activity-compose:${Versions.activityCompose}"
        const val splashScreen = "androidx.core:core-splashscreen:${Versions.splashScreen}"
        const val appCompat = "androidx.appcompat:appcompat:${Versions.appCompat}"
        const val fragmentKtx = "androidx.fragment:fragment-ktx:${Versions.fragment}"
        const val navigationFragmentKtx = "androidx.navigation:navigation-fragment-ktx:${Versions.navigation}"
        const val navigationUiKtx = "androidx.navigation:navigation-ui-ktx:${Versions.navigation}"
        // Root buildscript classpath entry for `id("androidx.navigation.safeargs.kotlin")`.
        const val navigationSafeArgsPlugin = "androidx.navigation:navigation-safe-args-gradle-plugin:${Versions.navigation}"
        const val lifecycleRuntimeKtx = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.androidxLifecycle}"
        const val material = "com.google.android.material:material:${Versions.material}"
    }

    object Android {
        const val playServicesAds = "com.google.android.gms:play-services-ads:${Versions.playServicesAds}"
        const val playServicesLocation = "com.google.android.gms:play-services-location:${Versions.playServicesLocation}"
        const val userMessagingPlatform = "com.google.android.ump:user-messaging-platform:${Versions.userMessagingPlatform}"
        const val mlkitDocumentScanner = "com.google.android.gms:play-services-mlkit-document-scanner:${Versions.mlkitDocumentScanner}"
        const val mlkitTextRecognition = "com.google.android.gms:play-services-mlkit-text-recognition:${Versions.mlkitTextRecognition}"
    }

    object Koin {
        const val core = "io.insert-koin:koin-core:${Versions.koin}"
        const val compose = "io.insert-koin:koin-compose:${Versions.koin}"
        const val android = "io.insert-koin:koin-android:${Versions.koin}"
        const val test = "io.insert-koin:koin-test:${Versions.koin}"
    }

    object KtorClient {
        const val core = "io.ktor:ktor-client-core:${Versions.ktor}"
        const val contentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor}"
        const val logging = "io.ktor:ktor-client-logging:${Versions.ktor}"
        const val serializationJson = "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}"
        const val okhttp = "io.ktor:ktor-client-okhttp:${Versions.ktor}"
        const val darwin = "io.ktor:ktor-client-darwin:${Versions.ktor}"
    }

    object Common {
        const val multiplatformSettings = "com.russhwolf:multiplatform-settings:${Versions.multiplatformSettings}"
        const val multiplatformSettingsTest = "com.russhwolf:multiplatform-settings-test:${Versions.multiplatformSettings}"
    }

    object SqlDelight {
        const val runtime = "app.cash.sqldelight:runtime:${Versions.sqldelight}"
        const val androidDriver = "app.cash.sqldelight:android-driver:${Versions.sqldelight}"
        const val nativeDriver = "app.cash.sqldelight:native-driver:${Versions.sqldelight}"
    }

    object Firebase {
        const val auth = "dev.gitlive:firebase-auth:${Versions.gitliveFirebase}"
        const val firestore = "dev.gitlive:firebase-firestore:${Versions.gitliveFirebase}"
        // Root buildscript classpath entry for `id("com.google.gms.google-services")`.
        const val googleServicesPlugin = "com.google.gms:google-services:${Versions.googleServicesPlugin}"
    }

    object Test {
        const val turbine = "app.cash.turbine:turbine:${Versions.turbine}"
    }
}
