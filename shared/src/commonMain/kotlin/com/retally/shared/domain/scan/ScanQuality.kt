package com.retally.shared.domain.scan

/**
 * Three-layer scan quality assessment for receipts.
 *
 * Layer 1 (before capture): platform document scanners handle edge detection and
 *   auto-capture — not represented here.
 * Layer 2 (after capture): [QualityReport] pixel checks on a downscaled grayscale copy.
 * Layer 3 (after OCR): [ContentReport] checks on what was actually extracted.
 *
 * Core principle: if the image passes quality checks but content is still missing,
 * the receipt itself is the problem (torn, faded, incomplete). A retake can fix the
 * camera, but it can't fix the paper — stop asking for retakes and save what we can.
 */

/** Outcome of the scan quality decision. */
enum class ScanOutcome {
    /** Good receipt — save normally. */
    ACCEPT,
    /** Bad capture — ask for a retake (capped). */
    RETAKE,
    /** Damaged/faded receipt but the total was read — save partial, user fills gaps. */
    PARTIAL,
    /** Damaged receipt with no total — keep image, show manual entry form. */
    MANUAL_ENTRY,
}

/**
 * Layer 2: pixel-level quality checks.
 * Thresholds are starting points — calibrate against ~20 good and ~20 bad scans
 * of real thermal-paper receipts, since paper and phone cameras vary widely.
 */
data class QualityReport(
    val sharpness: Double,
    val brightness: Double,
    val contrast: Double,
    val glareRatio: Double,
) {
    val issues: List<String>
        get() = buildList {
            if (sharpness < SHARPNESS_THRESHOLD) add("Image is blurry – hold steady")
            if (brightness < BRIGHTNESS_MIN) add("Too dark – add light")
            if (brightness > BRIGHTNESS_MAX) add("Overexposed")
            if (glareRatio > GLARE_THRESHOLD) add("Glare detected – tilt the receipt")
            if (contrast < CONTRAST_THRESHOLD) add("Low contrast – receipt may be faded")
        }

    val isAcceptable: Boolean get() = issues.isEmpty()

    companion object {
        const val SHARPNESS_THRESHOLD = 100.0  // Laplacian variance
        const val BRIGHTNESS_MIN = 60.0        // mean 0-255
        const val BRIGHTNESS_MAX = 220.0
        const val GLARE_THRESHOLD = 0.05       // fraction of pixels > 250
        const val CONTRAST_THRESHOLD = 30.0    // std-dev of brightness
    }
}

/**
 * Analyzes a downscaled grayscale copy of the captured receipt.
 * @param gray row-major luminance values 0..255, [width] x [height]
 */
fun analyzeQuality(gray: IntArray, width: Int, height: Int): QualityReport {
    var sum = 0.0
    var sumSq = 0.0
    var glare = 0
    for (p in gray) {
        sum += p
        sumSq += p * p
        if (p > 250) glare++
    }
    val n = gray.size
    val mean = sum / n
    val std = kotlin.math.sqrt(sumSq / n - mean * mean)

    // Laplacian variance (3x3 kernel: 0 1 0 / 1 -4 1 / 0 1 0)
    var lSum = 0.0
    var lSumSq = 0.0
    var count = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val i = y * width + x
            val lap = gray[i - width] + gray[i + width] + gray[i - 1] + gray[i + 1] - 4 * gray[i]
            lSum += lap
            lSumSq += lap.toDouble() * lap
            count++
        }
    }
    val lMean = lSum / count
    return QualityReport(
        sharpness = lSumSq / count - lMean * lMean,
        brightness = mean,
        contrast = std,
        glareRatio = glare.toDouble() / n,
    )
}

/**
 * Layer 3: content-level checks on what OCR actually extracted.
 * An image can pass every pixel check and still be unreadable.
 */
data class ContentReport(
    val hasMerchant: Boolean,
    val hasDate: Boolean,
    val hasTotal: Boolean,
    /** Line items + tax add up to the total (within tolerance). */
    val itemsMatchTotal: Boolean,
    /** Declared item count on the paper (e.g. "ITEMS SOLD: 12"), null if not printed. */
    val declaredItemCount: Int?,
    /** Parsed line item count, for comparison against [declaredItemCount]. */
    val parsedItemCount: Int,
    /** Contour area / convex hull area; < 0.90 suggests a torn edge. -1 if unavailable. */
    val outlineSolidity: Double = -1.0,
    /** OCR boxes touching the paper outline with partial words/prices. */
    val textClippedAtPaperEdge: Boolean = false,
) {
    /** True when the paper states a count and we parsed a different number of lines. */
    val itemCountMismatch: Boolean
        get() = declaredItemCount != null && declaredItemCount != parsedItemCount

    /**
     * Fields that are actually absent — not "math doesn't reconcile." A receipt with
     * 14 items whose prices don't sum exactly to the total has its items; they're
     * not missing. Math mismatch is exposed separately via [itemsMatchTotal].
     */
    val missingFields: Set<String>
        get() = buildSet {
            if (!hasMerchant) add("merchant")
            if (!hasDate) add("date")
            if (!hasTotal) add("total")
            if (parsedItemCount == 0) add("items")
            if (itemCountMismatch) add("itemCount")
        }
}

/** Maximum retakes before we stop asking (attempt is 1-based). */
const val MAX_SCAN_ATTEMPTS = 3

/**
 * Decides the scan outcome from quality + content reports.
 *
 * @param quality layer 2 pixel checks
 * @param content layer 3 content checks
 * @param attempt 1-based attempt number
 * @param previousMissing fields missing on the previous attempt (null on first attempt).
 *   If the same fields are missing on a clean retake, the damage is on the paper.
 */
fun decideScanOutcome(
    quality: QualityReport,
    content: ContentReport,
    attempt: Int,
    previousMissing: Set<String>?,
): ScanOutcome {
    val missing = content.missingFields
    if (missing.isEmpty()) return ScanOutcome.ACCEPT

    // Bad photo -> retake (capped at MAX_SCAN_ATTEMPTS).
    if (!quality.isAcceptable && attempt < MAX_SCAN_ATTEMPTS) return ScanOutcome.RETAKE

    val looksDamaged = content.outlineSolidity in 0.0..0.90 ||
        content.textClippedAtPaperEdge ||
        (previousMissing != null && missing == previousMissing)

    return when {
        looksDamaged && content.hasTotal -> ScanOutcome.PARTIAL
        looksDamaged -> ScanOutcome.MANUAL_ENTRY // no total = can't use for tracking
        attempt < MAX_SCAN_ATTEMPTS -> ScanOutcome.RETAKE
        else -> ScanOutcome.PARTIAL
    }
}
