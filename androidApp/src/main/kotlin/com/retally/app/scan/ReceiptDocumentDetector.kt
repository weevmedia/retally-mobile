/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds the receipt in a photo and flattens it — Android's answer to iOS VisionKit, which
 * keeps only the document and straightens it.
 *
 * Why GrabCut and not edge detection: receipts usually lie on a busy, light surface (a patterned
 * tablecloth, a counter) whose edges and brightness look just like paper, so contour finding
 * locks onto the pattern. GrabCut instead learns the paper's colours from where we *know* the
 * receipt is — the printed text found by OCR — and the background's colours from the photo's
 * border, then separates the two. The receipt region's minimum-area rectangle gives four
 * corners, and [warp] maps them to an upright image.
 *
 * Returns null when it can't find a believable receipt; the caller then keeps a plain crop
 * around the text.
 */
object ReceiptDocumentDetector {

    private const val TAG = "ReceiptDocument"
    private const val WORK_SIDE = 400.0

    private val ready: Boolean by lazy {
        runCatching { OpenCVLoader.initLocal() }.getOrElse {
            Log.w(TAG, "OpenCV failed to load", it)
            false
        }
    }

    /** Corners in [photo] pixels: top-left, top-right, bottom-right, bottom-left. */
    fun detect(photo: Bitmap, textBounds: Rect): Array<PointF>? {
        if (!ready) return null
        val scale = WORK_SIDE / max(photo.width, photo.height)
        val sw = max(1, (photo.width * scale).roundToInt())
        val sh = max(1, (photo.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(photo, sw, sh, true)
        val rgba = Mat()
        val rgb = Mat()
        val mask = Mat(sh, sw, CvType.CV_8UC1, Scalar(Imgproc.GC_PR_BGD.toDouble()))
        val bgModel = Mat()
        val fgModel = Mat()
        val foreground = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

            // Photo border = background for sure.
            val border = max(2, (0.02 * min(sw, sh)).toInt())
            val bg = Scalar(Imgproc.GC_BGD.toDouble())
            mask.rowRange(0, border).setTo(bg)
            mask.rowRange(sh - border, sh).setTo(bg)
            mask.colRange(0, border).setTo(bg)
            mask.colRange(sw - border, sw).setTo(bg)

            // The printed text = probably receipt; its inner core = receipt for sure.
            val l = (textBounds.left * scale).roundToInt().coerceIn(border, sw - border - 1)
            val t = (textBounds.top * scale).roundToInt().coerceIn(border, sh - border - 1)
            val r = (textBounds.right * scale).roundToInt().coerceIn(l + 1, sw - border)
            val b = (textBounds.bottom * scale).roundToInt().coerceIn(t + 1, sh - border)
            mask.submat(t, b, l, r).setTo(Scalar(Imgproc.GC_PR_FGD.toDouble()))
            val cx = (r - l) / 4
            val cy = (b - t) / 8
            if (r - l > 2 * cx + 1 && b - t > 2 * cy + 1) {
                mask.submat(t + cy, b - cy, l + cx, r - cx).setTo(Scalar(Imgproc.GC_FGD.toDouble()))
            }

            Imgproc.grabCut(rgb, mask, org.opencv.core.Rect(), bgModel, fgModel, 4, Imgproc.GC_INIT_WITH_MASK)

            // Foreground = GC_FGD (1) or GC_PR_FGD (3): the low bit is set.
            // bitwise_and needs a Mat, not a Scalar: a ones mask keeps only the low bit.
            Core.bitwise_and(mask, Mat(sh, sw, CvType.CV_8UC1, Scalar(1.0)), foreground)
            Core.multiply(foreground, Scalar(255.0), foreground)
            Imgproc.morphologyEx(
                foreground, foreground, Imgproc.MORPH_CLOSE,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)),
            )
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(foreground, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
            val share = Imgproc.contourArea(largest) / (sw.toDouble() * sh)
            if (share < 0.10 || share > 0.97) return null

            val box = Imgproc.minAreaRect(MatOfPoint2f(*largest.toArray()))
            val pts = arrayOfNulls<Point>(4)
            box.points(pts)
            val corners = order(pts.filterNotNull().map {
                // Back to photo pixels, kept inside the photo.
                PointF(
                    (it.x / scale).toFloat().coerceIn(0f, photo.width.toFloat()),
                    (it.y / scale).toFloat().coerceIn(0f, photo.height.toFloat()),
                )
            }) ?: return null

            // Sanity: the receipt must hold the centre of the printed text and be a real size.
            val width = (dist(corners[0], corners[1]) + dist(corners[3], corners[2])) / 2f
            val height = (dist(corners[0], corners[3]) + dist(corners[1], corners[2])) / 2f
            if (min(width, height) < min(photo.width, photo.height) * 0.15f) return null
            if (!contains(corners, PointF(textBounds.exactCenterX(), textBounds.exactCenterY()))) return null
            return corners
        } catch (e: Exception) {
            Log.w(TAG, "Receipt detection failed", e)
            return null
        } finally {
            if (small !== photo) small.recycle()
            rgba.release(); rgb.release(); mask.release(); bgModel.release(); fgModel.release(); foreground.release()
        }
    }

    /** Perspective-maps the four [corners] onto an upright rectangle (the flattened receipt). */
    fun warp(photo: Bitmap, corners: Array<PointF>): Bitmap {
        val (tl, tr, br, bl) = corners
        val outW = ((dist(tl, tr) + dist(bl, br)) / 2f).roundToInt().coerceAtLeast(1)
        val outH = ((dist(tl, bl) + dist(tr, br)) / 2f).roundToInt().coerceAtLeast(1)
        val src = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE) // corners clamped to the photo edge stay white, not black
        canvas.drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Orders four points as top-left, top-right, bottom-right, bottom-left. */
    private fun order(points: List<PointF>): Array<PointF>? {
        if (points.size != 4) return null
        val tl = points.minBy { it.x + it.y }
        val br = points.maxBy { it.x + it.y }
        val tr = points.maxBy { it.x - it.y }
        val bl = points.minBy { it.x - it.y }
        if (setOf(tl, tr, br, bl).size != 4) return null
        return arrayOf(tl, tr, br, bl)
    }

    private fun dist(a: PointF, b: PointF) = hypot(a.x - b.x, a.y - b.y)

    /** Point-in-convex-quad test (same side of every edge). */
    private fun contains(q: Array<PointF>, p: PointF): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) {
                if (sign == 0) sign = s else if (s != sign) return false
            }
        }
        return true
    }
}
