package com.retally.app.ui.scan

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.retally.app.location.NearbyStoreScheduler
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.scan.ScanReviewContract.Action
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.Closed
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.Saved
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.SaveToPhotos
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.ShareReceipt
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.ShowMessage
import com.retally.shared.presentation.scan.ScanReviewContract.State
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

/**
 * Receipt view. Reached from Home either with the recognized text of a fresh scan (saved on
 * arrival) or with the id of an existing receipt (Safe Args). Closing pops back to Home.
 */
class ScanReviewFragment : BaseComposeFragment<State, Action, SideEffect, ScanReviewViewModel>() {

    override val viewModel: ScanReviewViewModel by viewModel()
    private val args: ScanReviewFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            args.receiptId != null -> viewModel.onLoadReceipt(args.receiptId!!)
            args.recognizedText != null -> viewModel.onTextRecognized(args.recognizedText!!, args.imagePath)
        }
    }

    @Composable
    override fun ScreenContent(state: State) {
        ScanReviewScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            Closed -> findNavController().popBackStack()
            is Saved -> {
                // A new store may have arrived — rebuild the geofence list (no-op if toggle is off).
                NearbyStoreScheduler.refresh(requireContext())
            }
            is ShareReceipt -> shareReceipt(effect.imagePath)
            is SaveToPhotos -> saveToPhotos(effect.imagePath)
            is ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    private fun shareReceipt(imagePath: String?) {
        val path = imagePath ?: return
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share receipt"))
    }

    private fun saveToPhotos(imagePath: String?) {
        val path = imagePath ?: return
        val file = File(path)
        if (!file.exists()) return
        val context = requireContext()
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "retally_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ReTally")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            android.widget.Toast.makeText(context, "Saved to Photos", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
