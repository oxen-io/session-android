package org.thoughtcrime.securesms.recoverypassword

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.session.libsession.utilities.AppTextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import javax.inject.Inject

@HiltViewModel
class RecoveryPasswordViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application) {
    val prefs = AppTextSecurePreferences(application)

    val seed = MutableStateFlow<String?>(null)
    val mnemonic = seed.filterNotNull()
        .map { MnemonicCodec { MnemonicUtilities.loadFileContents(application, it) }.encode(it, MnemonicCodec.Language.Configuration.english) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun copyMnemonic() {
        prefs.setHasViewedSeed(true)
        ClipData.newPlainText("Seed", mnemonic.value)
            .let(application.clipboard::setPrimaryClip)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            seed.emit(IdentityKeyUtil.retrieve(application, IdentityKeyUtil.LOKI_SEED)
                ?: IdentityKeyUtil.getIdentityKeyPair(application).hexEncodedPrivateKey) // Legacy account
        }
    }
}

private val Context.clipboard get() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
