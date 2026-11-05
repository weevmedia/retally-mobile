/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

buildscript {
    val kotlinVersion: String by project

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        // Kept here (not in the plugins {} block) so Android Studio can offer updates and
        // every module applies plugins by id without repeating versions — as in Sidearm.
        classpath("com.android.tools.build:gradle:${Versions.agp}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.compose:compose-gradle-plugin:${Versions.composeMultiplatform}")
        classpath(Deps.AndroidX.navigationSafeArgsPlugin)
        classpath(Deps.Firebase.googleServicesPlugin)
        classpath(Deps.Firebase.crashlyticsGradlePlugin)
        classpath(Deps.Build.spotlessPlugin)
    }
}

/*
 * Copyright header on every source file. `./gradlew spotlessApply` adds it where missing,
 * `./gradlew spotlessCheck` fails the build when a file lacks it. Swift files get the same
 * header from Xcode's file template (iosApp/ReTally.xcodeproj/xcshareddata/IDETemplateMacros.plist).
 * Only the header is managed here; Spotless does not reformat code.
 */
apply(plugin = "com.diffplug.spotless")
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    val header = "/*\n * Copyright (c) \$YEAR Weev Media. All rights reserved.\n */\n\n"
    kotlin {
        target("shared/src/**/*.kt", "androidApp/src/**/*.kt")
        targetExclude("**/build/**", "**/java-gen/**")
        licenseHeader(header)
    }
    kotlinGradle {
        target("*.gradle.kts", "shared/*.gradle.kts", "androidApp/*.gradle.kts")
        licenseHeader(header, "(buildscript|pluginManagement|plugins|import|rootProject)")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Spotless applies Gradle's `base` plugin, which already creates `clean` (and it deletes the
// build directory). Configure that task rather than registering a second one.
tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
