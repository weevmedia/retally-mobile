/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.category

/**
 * Server-side (Gemini) item categories. The on-device keyword tagger gives an instant guess at
 * scan time; this refines it. Each distinct receipt line is classified once on the server and
 * cached for everyone.
 */
interface ItemCategoryClassifier {
    /**
     * Category id for each of [itemNames] (keys are the names exactly as passed), or null when
     * the call failed (offline, server error) — callers keep their current categories then.
     */
    suspend fun classify(storeName: String, itemNames: List<String>): Map<String, String>?
}

