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
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
