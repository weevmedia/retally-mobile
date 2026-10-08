import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose") version Versions.kotlin
    // Local SQLite via SQLDelight; plugin marker resolves from the Gradle Plugin Portal.
    id("app.cash.sqldelight") version Versions.sqldelight
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // SQLDelight's NativeSqliteDriver calls the system SQLite without linking it;
    // link libsqlite3 so the iOS framework (and its tests) resolve _sqlite3_* symbols.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.all {
            linkerOpts("-lsqlite3")
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // The Compose UI, the Redux stores and the DI entry points are all exported
            // through this one framework; Swift only hosts the root UIViewController.
        }
    }

    sourceSets {
        all {
            // kotlin.time.Instant/Clock (used throughout the domain) are still opt-in in Kotlin 2.2.
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain.dependencies {
            // Compose Multiplatform
            with(Deps.Compose.Multiplatform) {
                implementation(runtime)
                implementation(foundation)
                implementation(ui)
                implementation(componentsResources)
                implementation(materialIconsExtended)
                implementation(lifecycleRuntimeCompose)
                // Multiplatform ViewModel base for core.ViewModel (real AndroidX VM on Android).
                api(lifecycleViewModel)
            }
            // Material3 is versioned independently of CMP since 1.9 (no 1.9.3 artifact exists);
            // the Compose Gradle plugin resolves the matching release.
            implementation(compose.material3)

            // Redux plumbing
            implementation(Deps.Kotlinx.coroutinesCore)
            implementation(Deps.Kotlinx.serializationJson)
            implementation(Deps.Kotlinx.dateTime)

            // DI
            implementation(Deps.Koin.core)
            implementation(Deps.Koin.compose)

            // Cloud sync placeholder transport
            with(Deps.KtorClient) {
                implementation(core)
                implementation(contentNegotiation)
                implementation(logging)
                implementation(serializationJson)
            }

            // Firebase (Auth + Firestore) — anonymous identity and cloud copy of receipts
            with(Deps.Firebase) {
                implementation(auth)
                implementation(firestore)
            }

            // Local persistence
            // `api`: Settings is part of PlatformDependencies, which the apps construct.
            api(Deps.Common.multiplatformSettings)
            // SQLDelight runtime (generated ReTallyDatabase + query types).
            implementation(Deps.SqlDelight.runtime)

            // Image loading (Coil 3): remote store logos with memory/disk cache.
            // `networkKtor3` is required — core alone cannot fetch HTTP URLs.
            with(Deps.Coil) {
                implementation(compose)
                implementation(networkKtor3)
            }
        }
        androidMain.dependencies {
            implementation(Deps.Kotlinx.coroutinesAndroid)
            implementation(Deps.KtorClient.okhttp)
            implementation(Deps.Android.playServicesAds)
            implementation(Deps.Koin.android)
            implementation(Deps.SqlDelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(Deps.KtorClient.darwin)
            implementation(Deps.SqlDelight.nativeDriver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(Deps.Kotlinx.coroutinesTest)
            implementation(Deps.Test.turbine)
            implementation(Deps.Common.multiplatformSettingsTest)
        }
    }
}

// SQLDelight: .sq files live in shared/src/commonMain/sqldelight; the plugin generates
// ReTallyDatabase (queries -> com.retally.shared.db) for every Kotlin target.
sqldelight {
    databases {
        create("ReTallyDatabase") {
            packageName.set("com.retally.shared.db")
        }
    }
}

android {
    namespace = "com.retally.shared"
    compileSdk = Versions.androidCompileSdk
    defaultConfig {
        minSdk = Versions.androidMinSdk
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Compose Multiplatform resources (shared/src/commonMain/composeResources): the generated
// `Res` accessor lives in a fixed package so both apps and tests can reference it.
compose.resources {
    packageOfResClass = "com.retally.shared.resources"
    publicResClass = false
    generateResClass = always
}
