package com.retally.app.ui.home

import com.retally.app.R
import android.app.Activity
import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.retally.app.scan.ReceiptTextRecognizer
import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.home.HomeContract.Action
import com.retally.shared.presentation.home.HomeContract.SideEffect
import com.retally.shared.presentation.home.HomeContract.State
import com.retally.shared.presentation.home.HomeContract.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.tasks.await

/**
 * Android ViewModel for Home. The shared [Store] decides *what* happens (launch the scanner,
 * open the review); this class does the Android-specific *how*: the ML Kit document scanner
 * and on-device OCR. Results go back into the Store as [Action.TextRecognized] /
 * [Action.ScanFailed], which is what makes the flow identical on iOS.
 */
class HomeViewModel(
    override val store: Store,
    private val textRecognizer: ReceiptTextRecognizer,
    private val application: Application,
) : ViewModel<State, Action, SideEffect>() {

    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(3) // long receipts photographed in sections
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
        .build()

    /** Resolves the Play-services scanner UI intent; the fragment launches it. */
    fun requestScannerIntent(activity: Activity, onReady: (IntentSender) -> Unit) {
        viewModelScope.launch {
            runCatching { GmsDocumentScanning.getClient(scannerOptions).getStartScanIntent(activity).await() }
                .onSuccess(onReady)
                .onFailure { dispatch(Action.ScanFailed(application.getString(R.string.host_scanner_unavailable, it.message.orEmpty()).trim())) }
        }
    }

    /** Called with the scanner's activity result (null when the user backed out). */
    fun onScannerResult(result: GmsDocumentScanningResult?) {
        val pages: List<Uri> = result?.pages?.map { it.imageUri }.orEmpty()
        if (pages.isEmpty()) return // cancelled
        viewModelScope.launch {
            runCatching { textRecognizer.recognizeLines(pages) }
                .onSuccess { lines ->
                    // Keep the first page as the "original" the shopper can show a cashier.
                    val imagePath = withContext(Dispatchers.IO) { runCatching { keepScanImage(pages.first()) }.getOrNull() }
                    dispatch(Action.TextRecognized(lines, imagePath))
                }
                .onFailure { dispatch(Action.ScanFailed(application.getString(R.string.host_scan_read_failed))) }
        }
    }

    /** Copies the scanner's temp JPEG into app-private storage (survives the scanner cleaning up). */
    private fun keepScanImage(uri: Uri): String {
        val dir = File(application.filesDir, "receipts").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}.jpg")
        application.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }
}
