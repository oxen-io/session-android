package org.thoughtcrime.securesms.onboarding.loadaccount

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.crypto.MnemonicCodec.DecodingError.InputTooShort
import org.session.libsignal.crypto.MnemonicCodec.DecodingError.InvalidWord
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import javax.inject.Inject

class LoadAccountEvent(val mnemonic: ByteArray)

internal data class State(
    val recoveryPhrase: String = "",
    val isTextErrorColor: Boolean = false,
    val error: String? = null
)

@HiltViewModel
internal class LoadAccountViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application) {
    private val state = MutableStateFlow(State())
    val stateFlow = state.asStateFlow()

    private val _events = MutableSharedFlow<LoadAccountEvent>()
    val events = _events.asSharedFlow()

    private val _qrErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val qrErrors = _qrErrors.asSharedFlow()
        .mapNotNull { application.getString(R.string.qrNotRecoveryPassword) }

    private val codec by lazy { MnemonicCodec { MnemonicUtilities.loadFileContents(getApplication(), it) } }

    fun onContinue() {
        viewModelScope.launch {
            try {
                codec.sanitizeAndDecodeAsByteArray(state.value.recoveryPhrase).let(::onSuccess)
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    fun onScanQrCode(string: String) {
        viewModelScope.launch {
            try {
                codec.decodeMnemonicOrHexAsByteArray(string).let(::onSuccess)
            } catch (e: Exception) {
                onQrCodeScanFailure(e)
            }
        }
    }

    fun onChange(recoveryPhrase: String) {
        state.update { it.copy(recoveryPhrase = recoveryPhrase, isTextErrorColor = false) }
    }

    private fun onSuccess(seed: ByteArray) {
        viewModelScope.launch { _events.emit(LoadAccountEvent(seed)) }
    }

    private fun onFailure(error: Throwable) {
        state.update {
            it.copy(
                isTextErrorColor = true,
                error = when (error) {
                    is InvalidWord -> R.string.recoveryPasswordErrorMessageIncorrect
                    is InputTooShort -> R.string.recoveryPasswordErrorMessageShort
                    else -> R.string.recoveryPasswordErrorMessageGeneric
                }.let(application::getString)
            )
        }
    }

    private fun onQrCodeScanFailure(error: Throwable) {
        viewModelScope.launch { _qrErrors.emit(error) }
    }
}
