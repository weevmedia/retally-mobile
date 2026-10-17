package com.retally.app.ui.scan

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.retally.app.R
import com.retally.app.scan.ReceiptTextRecognizer
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * ML Kit Document Scanner: Google's system document scanner with auto edge detection,
 * perspective correction, and auto-capture. Goes straight to review — no extra taps.
 *
 * Replaces the custom CameraX scanner for better document detection.
 */
class MlKitScannerFragment : Fragment() {

    private val textRecognizer: ReceiptTextRecognizer by inject()

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                scanResult?.let { handleScanResult(it) }
            } else {
                // Cancelled — go back.
                findNavController().popBackStack()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // No UI — launch the scanner immediately.
        return View(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        launchScanner()
    }

    private fun launchScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(requireActivity())
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                // Failed to launch — go back.
                findNavController().popBackStack()
            }
    }

    private fun handleScanResult(result: GmsDocumentScanningResult) {
        val pages = result.pages
        if (pages.isNullOrEmpty()) {
            findNavController().popBackStack()
            return
        }
        val imageUri = pages[0].imageUri
        lifecycleScope.launch {
            val lines = runCatching { textRecognizer.recognizeLines(listOf(imageUri)) }
                .getOrDefault(emptyList())
            if (lines.isNotEmpty()) {
                val bundle = Bundle().apply {
                    putString("recognizedText", lines.joinToString("\n"))
                    putString("imagePath", imageUri.toString())
                }
                findNavController().navigate(R.id.action_customScanner_to_scanReview, bundle)
            } else {
                // No text — go back to let them retry.
                findNavController().popBackStack()
            }
        }
    }
}
