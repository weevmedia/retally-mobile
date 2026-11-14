/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.config

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Pages the app opens. Each can be overridden from the admin console (`config/app.links`); a
 * blank value there keeps the default below.
 */
@Serializable
data class AppLinks(
    // Firebase Hosting (retally-backend/firestore/hosting).
    val privacyPolicy: String = "https://retally-c5212.web.app/privacy",
    val terms: String = "https://retally-c5212.web.app/terms",
    val support: String = "https://retally-c5212.web.app/support",
    /** Blank = the built-in How it works screen; a URL opens that page instead. */
    val howItWorks: String = "",
)

/** Kill switches from the admin console (`config/app.features`). On unless set to false. */
@Serializable
data class AppFeatures(
    val ads: Boolean = true,
    val recalls: Boolean = true,
)

@Serializable
data class AppConfig(
    val links: AppLinks = AppLinks(),
    val features: AppFeatures = AppFeatures(),
)

/**
 * Server-driven links and feature switches. [config] starts from the last cached copy (or the
 * defaults) so it is usable instantly and offline; [refresh] pulls the latest in the background.
 */
interface AppConfigRepository {
    val config: StateFlow<AppConfig>

    /** Re-reads the server copy unless it was fetched recently (or [force]). Never throws. */
    suspend fun refresh(force: Boolean = false)
}
