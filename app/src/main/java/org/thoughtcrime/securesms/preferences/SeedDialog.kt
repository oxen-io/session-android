package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities

class SeedDialog: DialogFragment() {
    private val seed by lazy {
        val hexEncodedSeed = IdentityKeyUtil.retrieve(requireContext(), IdentityKeyUtil.LOKI_SEED)
            ?: IdentityKeyUtil.getIdentityKeyPair(requireContext()).hexEncodedPrivateKey // Legacy account

        MnemonicCodec { fileName -> MnemonicUtilities.loadFileContents(requireContext(), fileName) }
            .encode(hexEncodedSeed, MnemonicCodec.Language.Configuration.english)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(R.string.dialog_seed_title)
        text(R.string.dialog_seed_explanation)
        text(seed, R.style.SessionIDTextView)
        button(R.string.copy, R.string.AccessibilityId_copy_recovery_phrase) { copySeed() }
        button(R.string.close) { dismiss() }
    }

    private fun copySeed() {
        val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Seed", seed)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        dismiss()
    }
}
