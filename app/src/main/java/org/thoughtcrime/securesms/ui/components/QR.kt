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
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import java.util.concurrent.Executors

private const val TAG = "NewMessageFragment"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MaybeScanQrCode(
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

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        cameraProvider.get().bindToLifecycle(
            LocalLifecycleOwner.current,
            selector,
            preview,
            buildAnalysisUseCase(scanner, onScan)
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
    scanner: BarcodeScanner,
    onBarcodeScanned: (String) -> Unit
): ImageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build().apply {
        setAnalyzer(Executors.newSingleThreadExecutor(), Analyzer(scanner, onBarcodeScanned))
    }

class Analyzer(
    private val scanner: BarcodeScanner,
    private val onBarcodeScanned: (String) -> Unit
): ImageAnalysis.Analyzer {
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        InputImage.fromMediaImage(
            image.image!!,
            image.imageInfo.rotationDegrees
        ).let(scanner::process).apply {
            addOnSuccessListener { barcodes ->
                barcodes.forEach {
                    it.rawValue?.let(onBarcodeScanned)
                }
            }
            addOnCompleteListener {
                image.close()
            }
        }
    }
}
