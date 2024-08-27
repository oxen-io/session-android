package org.thoughtcrime.securesms.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType

private const val TAG = "NewMessageFragment"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
        errors: Flow<String>,
        onClickSettings: () -> Unit = LocalContext.current.run { {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }.let(::startActivity)
        } },
        onScan: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LocalSoftwareKeyboardController.current?.hide()

        val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

        if (cameraPermissionState.status.isGranted) {
            ScanQrCode(errors, onScan)
        } else if (cameraPermissionState.status.shouldShowRationale) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 60.dp)
            ) {
                Text(
                    stringResource(R.string.activity_link_camera_permission_permanently_denied_configure_in_settings),
                    style = LocalType.current.base,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(LocalDimensions.current.spacing))
                OutlineButton(
                    stringResource(R.string.sessionSettings),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = onClickSettings
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LocalDimensions.current.xlargeSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(stringResource(R.string.fragment_scan_qr_code_camera_access_explanation),
                    style = LocalType.current.xl, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))
                PrimaryOutlineButton(
                    stringResource(R.string.cameraGrantAccess),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { cameraPermissionState.run { launchPermissionRequest() } }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ScanQrCode(errors: Flow<String>, onScan: (String) -> Unit) {
    val localContext = LocalContext.current
    val cameraProvider = remember { ProcessCameraProvider.getInstance(localContext) }

    val preview = Preview.Builder().build()
    val selector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()

    runCatching {
        cameraProvider.get().unbindAll()

        cameraProvider.get().bindToLifecycle(
            LocalLifecycleOwner.current,
            selector,
            preview,
            buildAnalysisUseCase(QRCodeReader(), onScan)
        )

    }.onFailure { Log.e(TAG, "error binding camera", it) }

    DisposableEffect(cameraProvider) {
        onDispose {
            cameraProvider.get().unbindAll()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        errors.collect { error ->
            snackbarHostState
                .takeIf { it.currentSnackbarData == null }
                ?.run {
                    scope.launch {
                        // showSnackbar() suspends until the Snackbar is dismissed.
                        // Launch in new scope so we drop new QR scan events, to prevent spamming
                        // snackbars to the user, or worse, queuing a chain of snackbars one after
                        // another to show and hide for the next minute or 2.
                        // Don't use debounce() because many QR scans can come through each second,
                        // and each scan could restart the timer which could mean no scan gets
                        // through until the user stops scanning; quite perplexing.
                        snackbarHostState.showSnackbar(message = error)
                    }
                }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(LocalDimensions.current.smallSpacing)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.padding(LocalDimensions.current.smallSpacing)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { PreviewView(it).apply { preview.setSurfaceProvider(surfaceProvider) } }
            )

            Box(
                Modifier
                    .aspectRatio(1f)
                    .padding(LocalDimensions.current.spacing)
                    .clip(shape = RoundedCornerShape(26.dp))
                    .background(Color(0x33ffffff))
                    .align(Alignment.Center)
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun buildAnalysisUseCase(
    scanner: QRCodeReader,
    onBarcodeScanned: (String) -> Unit
): ImageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build().apply {
        setAnalyzer(Executors.newSingleThreadExecutor(), QRCodeAnalyzer(scanner, onBarcodeScanned))
    }

class QRCodeAnalyzer(
    private val qrCodeReader: QRCodeReader,
    private val onBarcodeScanned: (String) -> Unit
): ImageAnalysis.Analyzer {

    // Note: This analyze method is called once per frame of the camera feed.
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        // Grab the image data as a byte array so we can generate a PlanarYUVLuminanceSource from it
        val buffer = image.planes[0].buffer
        buffer.rewind()
        val imageBytes = ByteArray(buffer.capacity())
        buffer.get(imageBytes) // IMPORTANT: This transfers data from the buffer INTO the imageBytes array, although it looks like it would go the other way around!

        // ZXing requires data as a BinaryBitmap to scan for QR codes, and to generate that we need to feed it a PlanarYUVLuminanceSource
        val luminanceSource = PlanarYUVLuminanceSource(imageBytes, image.width, image.height, 0, 0, image.width, image.height, false)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))

        // Attempt to extract a QR code from the binary bitmap, and pass it through to our `onBarcodeScanned` method if we find one
        try {
            val result: Result = qrCodeReader.decode(binaryBitmap)
            val resultTxt = result.text
            // No need to close the image here - it'll always make it to the end, and calling `onBarcodeScanned`
            // with a valid contact / recovery phrase / community code will stop calling this `analyze` method.
            onBarcodeScanned(resultTxt)
        }
        catch (nfe: NotFoundException) { /* Hits if there is no QR code in the image           */ }
        catch (fe: FormatException)    { /* Hits if we found a QR code but failed to decode it */ }
        catch (ce: ChecksumException)  { /* Hits if we found a QR code which is corrupted      */ }
        catch (e: Exception) {
            // Hits if there's a genuine problem
            Log.e("QR", "error", e)
        }

        // Remember to close the image when we're done with it!
        // IMPORTANT: It is CLOSING the image that allows this method to run again! If we don't
        // close the image this method runs precisely ONCE and that's it, which is essentially useless.
        image.close()
    }
}
