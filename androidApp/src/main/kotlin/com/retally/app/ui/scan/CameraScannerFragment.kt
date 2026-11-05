/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.scan

import com.retally.shared.ui.theme.ReTallyTheme
import com.retally.shared.ui.scan.ScanCookingAnimation
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.retally.app.R
import com.retally.app.scan.ReceiptDocumentDetector
import com.retally.app.scan.ReceiptTextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * ReTally's own receipt camera. Replaces Google's document scanner, whose last step always
 * shows a review screen with a Next button.
 *
 * Live frames run through on-device text recognition (which also warms the model, so the read
 * after capture is quick). When enough receipt text fills the frame and holds still for about a
 * second, the photo is taken automatically and goes straight to the receipt screen. The
 * shutter button captures at any time; the gallery button reads a photo instead.
 */
class CameraScannerFragment : Fragment() {

    private val textRecognizer: ReceiptTextRecognizer by inject()

    private enum class Phase { Starting, Searching, Steady, Capturing, Reading, NoPermission }

    private var phase by mutableStateOf(Phase.Starting)
    private var steadyProgress by mutableStateOf(0f)
    private var torchOn by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var analysisExecutor: ExecutorService? = null
    private val busy = AtomicBoolean(false)

    private val liveRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Auto-capture tracking (analysis thread only).
    private var lastAnalysisAt = 0L
    private var steadyFrames = 0
    private var lastCenter: Pair<Float, Float>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            phase = if (granted) Phase.Searching else Phase.NoPermission
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) readAndOpen(uri, copyFirst = true) else busy.set(false)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { ScannerContent() }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analysisExecutor = Executors.newSingleThreadExecutor()
        if (hasCameraPermission()) {
            phase = Phase.Searching
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from system Settings with the permission now granted.
        if (phase == Phase.NoPermission && hasCameraPermission()) phase = Phase.Searching
    }

    override fun onDestroyView() {
        super.onDestroyView()
        analysisExecutor?.shutdown()
        analysisExecutor = null
        camera = null
        imageCapture = null
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // ---------- camera ----------

    private fun bindCamera(previewView: PreviewView) {
        val context = requireContext()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (view == null) return@addListener
            val provider = providerFuture.get()
            // 16:9 (portrait 9:16) so the photo covers the full height of the screen, not the
            // 4:3 middle of it — long receipts fit top to bottom.
            val tall = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(tall).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setResolutionSelector(tall)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(tall)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysisExecutor?.let { executor -> analysis.setAnalyzer(executor, ::analyze) }
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                imageCapture = capture
                camera?.cameraControl?.enableTorch(torchOn)
            } catch (e: Exception) {
                message = getString(R.string.scanner_camera_failed)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Looks for receipt text in a live frame and decides when to auto-capture. */
    @SuppressLint("UnsafeOptInUsageError")
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val media = proxy.image
        if (busy.get() || media == null || now - lastAnalysisAt < ANALYSIS_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastAnalysisAt = now
        val rotation = proxy.imageInfo.rotationDegrees
        // Text boxes come back in upright coordinates, so swap the frame size for portrait.
        val frameW = (if (rotation % 180 == 0) proxy.width else proxy.height).toFloat()
        val frameH = (if (rotation % 180 == 0) proxy.height else proxy.width).toFloat()
        liveRecognizer.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { text ->
                val boxes = text.textBlocks.flatMap { it.lines }.mapNotNull { it.boundingBox }
                onFrameText(boxes, frameW, frameH)
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onFrameText(boxes: List<Rect>, frameW: Float, frameH: Float) {
        if (busy.get()) return
        val union = boxes.fold<Rect, Rect?>(null) { acc, r -> acc?.apply { union(r) } ?: Rect(r) }
        val looksLikeReceipt = union != null && boxes.size >= MIN_LINES && union.height() / frameH >= MIN_TEXT_HEIGHT
        if (!looksLikeReceipt) {
            steadyFrames = 0
            lastCenter = null
            updatePhase(Phase.Searching, 0f)
            return
        }
        val center = union!!.exactCenterX() / frameW to union.exactCenterY() / frameH
        val previous = lastCenter
        val still = previous == null ||
            (abs(center.first - previous.first) < MAX_DRIFT && abs(center.second - previous.second) < MAX_DRIFT)
        lastCenter = center
        steadyFrames = if (still) steadyFrames + 1 else 1
        updatePhase(Phase.Steady, (steadyFrames.toFloat() / STEADY_FRAMES_TO_CAPTURE).coerceAtMost(1f))
        if (steadyFrames >= STEADY_FRAMES_TO_CAPTURE) {
            view?.post { capture() }
        }
    }

    private fun updatePhase(next: Phase, progress: Float) {
        view?.post {
            if (phase == Phase.Searching || phase == Phase.Steady) {
                phase = next
                steadyProgress = progress
            }
        }
    }

    private fun capture() {
        val capture = imageCapture ?: return
        if (!busy.compareAndSet(false, true)) return
        phase = Phase.Capturing
        message = null
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val file = newReceiptFile()
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (view == null) return
                    readAndOpen(Uri.fromFile(file), copyFirst = false)
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    if (view == null) return
                    resumeSearching(getString(R.string.scanner_capture_failed))
                }
            },
        )
    }

    // ---------- reading ----------

    /** OCR the photo, then open the receipt screen (the scanner leaves the back stack). */
    private fun readAndOpen(uri: Uri, copyFirst: Boolean) {
        busy.set(true)
        phase = Phase.Reading
        viewLifecycleOwner.lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching { if (copyFirst) copyToReceipts(uri) else uri.path!! }.getOrNull()
                    // Bake the rotation into the pixels (see uprightJpeg) before anything reads it.
                    ?.also { runCatching { uprightJpeg(File(it)) } }
            }
            if (path == null) {
                resumeSearching(getString(R.string.host_scan_read_failed))
                return@launch
            }
            val page = runCatching { textRecognizer.recognizePage(Uri.fromFile(File(path))) }.getOrNull()
            val lines = page?.lines.orEmpty()
            if (lines.isEmpty()) {
                withContext(Dispatchers.IO) { File(path).delete() }
                resumeSearching(getString(R.string.scanner_no_text))
                return@launch
            }
            // Keep just the receipt, like iOS: find the paper and straighten it; if that fails,
            // crop to the printed text plus a margin.
            var finalLines = lines
            page?.textBounds?.let { bounds ->
                val flattened = withContext(Dispatchers.Default) { runCatching { flattenReceipt(File(path), bounds) }.getOrDefault(false) }
                if (flattened) {
                    // Read the straightened receipt again — rows line up better than on the angled photo.
                    runCatching { textRecognizer.recognizePage(Uri.fromFile(File(path))) }.getOrNull()
                        ?.lines?.takeIf { it.isNotEmpty() }?.let { finalLines = it }
                } else {
                    withContext(Dispatchers.IO) { runCatching { cropToReceipt(File(path), bounds) } }
                }
            }
            val bundle = Bundle().apply {
                putString("recognizedText", finalLines.joinToString("\n"))
                putString("imagePath", path)
            }
            findNavController().navigate(R.id.action_customScanner_to_scanReview, bundle)
        }
    }

    private fun resumeSearching(why: String) {
        message = why
        viewLifecycleOwner.lifecycleScope.launch {
            delay(MESSAGE_MS)
            if (message == why) message = null
        }
        steadyFrames = 0
        lastCenter = null
        phase = if (hasCameraPermission()) Phase.Searching else Phase.NoPermission
        busy.set(false)
    }

    private fun newReceiptFile(): File {
        val dir = File(requireContext().filesDir, "receipts").apply { mkdirs() }
        return File(dir, "${System.currentTimeMillis()}.jpg")
    }

    private fun copyToReceipts(uri: Uri): String {
        val target = newReceiptFile()
        requireContext().contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }

    /**
     * CameraX writes the sensor's landscape pixels plus an EXIF "rotate 90°" tag. Anything that
     * ignores EXIF (the cloud copy, some viewers, the AI upload) then shows the receipt sideways.
     * Rotate the pixels upright and re-save without the tag; also cap the long side at
     * [MAX_IMAGE_SIDE] px so a 12 MP photo doesn't cost memory, storage and upload time.
     */
    private fun uprightJpeg(file: File) {
        val orientation = android.media.ExifInterface(file.absolutePath)
            .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (degrees == 0f && longSide <= MAX_IMAGE_SIDE) return // already upright and small enough
        var sample = 1
        while (longSide / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = android.graphics.BitmapFactory.decodeFile(
            file.absolutePath,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return
        val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = android.graphics.Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (degrees != 0f) postRotate(degrees)
        }
        val upright = android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        file.outputStream().use { upright.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
    }

    /**
     * Crops the saved photo to the receipt: the box around all recognised text, widened by a
     * margin so the paper's edges and the store logo above the text stay in. Skipped when the
     * text already fills most of the photo (nothing to gain) or the box looks wrong.
     */
    private fun cropToReceipt(file: File, text: Rect) {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || text.width() <= 0 || text.height() <= 0) return
        val marginX = (text.width() * CROP_MARGIN_X).toInt()
        val marginY = (text.height() * CROP_MARGIN_Y).toInt()
        val crop = Rect(
            (text.left - marginX).coerceAtLeast(0),
            (text.top - marginY).coerceAtLeast(0),
            (text.right + marginX).coerceAtMost(w),
            (text.bottom + marginY).coerceAtMost(h),
        )
        val keeps = crop.width().toFloat() * crop.height() / (w.toFloat() * h)
        // Only skip when the crop would remove almost nothing, or looks implausibly narrow.
        if (keeps > 0.95f || crop.width() < w * 0.2f) return
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return
        val cropped = android.graphics.Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
        file.outputStream().use { cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()
    }

    /** Finds the receipt, straightens it and overwrites [file]. False when no receipt was found. */
    private fun flattenReceipt(file: File, text: Rect): Boolean {
        val photo = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return false
        try {
            val corners = ReceiptDocumentDetector.detect(photo, text) ?: return false
            val flat = ReceiptDocumentDetector.warp(photo, corners)
            file.outputStream().use { flat.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            flat.recycle()
            return true
        } finally {
            photo.recycle()
        }
    }

    private fun pickFromGallery() {
        if (!busy.compareAndSet(false, true)) return
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        camera?.cameraControl?.enableTorch(torchOn)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null)),
        )
    }

    // ---------- UI ----------

    @Composable
    private fun ScannerContent() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (phase != Phase.NoPermission && phase != Phase.Starting) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            post { bindCamera(this) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // The frame runs from the top of the screen (under the status bar; the close and
                // flash buttons float over it) down to just above the shutter.
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                FrameGuide(
                    ready = phase == Phase.Steady,
                    progress = steadyProgress,
                    topInset = insets.calculateTopPadding() + 8.dp,
                    bottomInset = insets.calculateBottomPadding() + SHUTTER_AREA,
                )
            }

            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Rounded.Close, stringResource(R.string.scanner_close)) { findNavController().popBackStack() }
                    if (phase != Phase.NoPermission) {
                        RoundButton(
                            if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            stringResource(R.string.scanner_flash),
                        ) { toggleTorch() }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (phase == Phase.NoPermission) {
                    PermissionPrompt()
                    Spacer(Modifier.weight(1f))
                } else {
                    HintPill()
                    // Keeps the hint inside the frame's bottom edge, clear of the corners.
                    Spacer(Modifier.height(36.dp))
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 28.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Rounded.PhotoLibrary, stringResource(R.string.scanner_gallery)) { pickFromGallery() }
                    if (phase != Phase.NoPermission) {
                        Shutter(enabled = phase == Phase.Searching || phase == Phase.Steady) { capture() }
                    }
                    Spacer(Modifier.size(48.dp))
                }
            }

            if (phase == Phase.Capturing || phase == Phase.Reading) {
                // Same "reading" moment as iOS: the receipt animation on a plain page (the review
                // screen continues the same animation if parsing needs longer).
                ReTallyTheme {
                    Box(
                        Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScanCookingAnimation()
                    }
                }
            }
        }
    }

    @Composable
    private fun HintPill() {
        val text = message ?: when (phase) {
            Phase.Steady -> stringResource(R.string.scanner_hold_steady)
            else -> stringResource(R.string.scanner_point_at_receipt)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }

    @Composable
    private fun PermissionPrompt() {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.scanner_permission_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.scanner_permission_body),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    openAppSettings()
                }
            }) {
                Text(stringResource(R.string.scanner_permission_allow), color = BRAND, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    /** Dims everything outside a tall receipt-shaped window; the corners turn green as it locks on. */
    @Composable
    private fun FrameGuide(ready: Boolean, progress: Float, topInset: Dp, bottomInset: Dp) {
        val animated by animateFloatAsState(if (ready) progress else 0f, label = "lock")
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val w = size.width - 2 * 16.dp.toPx()
            val h = size.height - topInset.toPx() - bottomInset.toPx()
            val topLeft = Offset((size.width - w) / 2f, topInset.toPx())
            val radius = CornerRadius(28.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.45f))
            drawRoundRect(Color.Transparent, topLeft, Size(w, h), radius, blendMode = BlendMode.Clear)

            val color = if (ready) BRAND else Color.White
            val arm = 36.dp.toPx()
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val r = radius.x
            val left = topLeft.x
            val top = topLeft.y
            val right = left + w
            val bottom = top + h
            // Four corner brackets.
            listOf(
                Triple(Offset(left, top + r + arm), Offset(left, top + r), 180f),
                Triple(Offset(right, top + r + arm), Offset(right, top + r), 270f),
                Triple(Offset(right, bottom - r - arm), Offset(right, bottom - r), 0f),
                Triple(Offset(left, bottom - r - arm), Offset(left, bottom - r), 90f),
            ).forEach { (from, to, start) ->
                drawLine(color, from, to, stroke.width, StrokeCap.Round)
                val arcTopLeft = when (start) {
                    180f -> Offset(left, top)
                    270f -> Offset(right - 2 * r, top)
                    0f -> Offset(right - 2 * r, bottom - 2 * r)
                    else -> Offset(left, bottom - 2 * r)
                }
                drawArc(color, start, 90f, false, arcTopLeft, Size(2 * r, 2 * r), style = stroke)
                val horizontalFrom = when (start) {
                    180f -> Offset(left + r, top) to Offset(left + r + arm, top)
                    270f -> Offset(right - r, top) to Offset(right - r - arm, top)
                    0f -> Offset(right - r, bottom) to Offset(right - r - arm, bottom)
                    else -> Offset(left + r, bottom) to Offset(left + r + arm, bottom)
                }
                drawLine(color, horizontalFrom.first, horizontalFrom.second, stroke.width, StrokeCap.Round)
            }
            // Lock-on progress inside the frame, just under the close / flash buttons.
            if (animated > 0f) {
                val barW = w * 0.5f
                val y = top + 76.dp.toPx()
                val x = (size.width - barW) / 2f
                drawLine(Color.White.copy(alpha = 0.3f), Offset(x, y), Offset(x + barW, y), 4.dp.toPx(), StrokeCap.Round)
                drawLine(BRAND, Offset(x, y), Offset(x + barW * animated, y), 4.dp.toPx(), StrokeCap.Round)
            }
        }
    }

    @Composable
    private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
    }

    @Composable
    private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
        val label = stringResource(R.string.scanner_take_photo)
        Box(
            Modifier
                .size(76.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        )
    }

    private companion object {
        val BRAND = Color(0xFF10B981)
        /** Run text recognition on at most ~2 frames a second. */
        const val ANALYSIS_INTERVAL_MS = 450L
        /** A receipt shows at least this many text lines… */
        const val MIN_LINES = 5
        /** …spanning at least this share of the frame's height. */
        const val MIN_TEXT_HEIGHT = 0.25f
        /** How far (share of frame) the text may move between frames and still count as steady. */
        const val MAX_DRIFT = 0.04f
        /** Steady analysed frames before auto-capture (~1.3 s). */
        const val STEADY_FRAMES_TO_CAPTURE = 3
        const val MESSAGE_MS = 3_000L
        /** Longest side of a saved receipt photo — plenty for OCR, the AI read and zooming in. */
        const val MAX_IMAGE_SIDE = 2400
        const val JPEG_QUALITY = 90
        /** Crop margins around the text, as a share of the text box (paper edges, logo, barcode). */
        const val CROP_MARGIN_X = 0.04f
        const val CROP_MARGIN_Y = 0.04f
        /** Shutter row (76dp button + 28dp bottom padding) plus a 12dp gap above it. */
        val SHUTTER_AREA = 116.dp
    }
}
