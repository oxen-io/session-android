package org.thoughtcrime.securesms.util

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentScanQrCodePlaceholderBinding
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY

class ScanQRCodePlaceholderFragment: Fragment() {
    private lateinit var binding: FragmentScanQrCodePlaceholderBinding
    var delegate: ScanQRCodePlaceholderFragmentDelegate? = null

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View {
        binding = FragmentScanQrCodePlaceholderBinding.inflate(layoutInflater, viewGroup, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.grantCameraAccessButton.setOnClickListener { delegate?.requestCameraAccess() }

        binding.needCameraPermissionsTV.text = Phrase.from(context, R.string.cameraGrantAccessQr)
                                                  .put(APP_NAME_KEY, getString(R.string.app_name))
                                                  .format()
    }
}

interface ScanQRCodePlaceholderFragmentDelegate {
    fun requestCameraAccess()
}