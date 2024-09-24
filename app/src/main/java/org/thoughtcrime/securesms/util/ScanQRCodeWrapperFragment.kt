package org.thoughtcrime.securesms.util

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.emptyFlow
import org.thoughtcrime.securesms.ui.components.QRScannerScreen
import org.thoughtcrime.securesms.ui.createThemedComposeView

class ScanQRCodeWrapperFragment : Fragment() {

    companion object {
        const val FRAGMENT_TAG = "ScanQRCodeWrapperFragment_FRAGMENT_TAG"
    }

    var delegate: ScanQRCodeWrapperFragmentDelegate? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        createThemedComposeView {
            QRScannerScreen(emptyFlow(), onScan = {
                delegate?.handleQRCodeScanned(it)
            })
    }
}

fun interface ScanQRCodeWrapperFragmentDelegate {

    fun handleQRCodeScanned(string: String)
}