package org.thoughtcrime.securesms.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.squareup.phrase.Phrase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.findActivity
import org.thoughtcrime.securesms.ui.getSubbedString
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import java.util.concurrent.Executors

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

        val context = LocalContext.current
        val permission = Manifest.permission.CAMERA
        val cameraPermissionState = rememberPermissionState(permission)

        var showCameraPermissionDialog by remember { mutableStateOf(false) }

        if (cameraPermissionState.status.isGranted) {
            ScanQrCode(errors, onScan)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LocalDimensions.current.xlargeSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.cameraGrantAccessQr).let { txt ->
                        val c = LocalContext.current
                        Phrase.from(txt).put(APP_NAME_KEY, c.getString(R.string.app_name)).format().toString()
                    },
                    style = LocalType.current.xl,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))
                PrimaryOutlineButton(
                    stringResource(R.string.cameraGrantAccess),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // NOTE: We used to use the Accompanist's way to handle permissions in compose
                        // but it doesn't seem to offer a solution when a user manually changes a permission
                        // to 'Ask every time' form the app's settings.
                        // So we are using our custom implementation. ONE IMPORTANT THING with this approach
                        // is that we need to make sure every activity where this composable is used NEED to
                        // implement `onRequestPermissionsResult` (see LoadAccountActivity.kt for an example)
                        Permissions.with(context.findActivity())
                            .request(permission)
                            .withPermanentDenialDialog(
                                context.getSubbedString(R.string.permissionsCameraDenied,
                                    APP_NAME_KEY to context.getString(R.string.app_name))
                            ).execute()
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // camera permission denied permanently dialog
        if(showCameraPermissionDialog){
            AlertDialog(
                onDismissRequest = { showCameraPermissionDialog = false },
                title = stringResource(R.string.permissionsRequired),
                text = context.getSubbedString(R.string.permissionsCameraDenied,
                    APP_NAME_KEY to context.getString(R.string.app_name)),
                buttons = listOf(
                    DialogButtonModel(
                        text = GetString(stringResource(id = R.string.sessionSettings)),
                        onClick = onClickSettings
                    ),
                    DialogButtonModel(
                        GetString(stringResource(R.string.cancel))
                    )
                )
            )
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
