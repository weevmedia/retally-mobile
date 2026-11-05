/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "ReTally"

include(":shared", ":androidApp")
