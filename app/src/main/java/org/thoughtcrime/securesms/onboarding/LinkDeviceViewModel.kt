package org.thoughtcrime.securesms.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.crypto.MnemonicCodec.DecodingError.InputTooShort
import org.session.libsignal.crypto.MnemonicCodec.DecodingError.InvalidWord
import org.session.libsignal.utilities.Hex
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class LinkDeviceEvent(val mnemonic: ByteArray)

@HiltViewModel
class LinkDeviceViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application) {
    private val QR_ERROR_TIME = 3.seconds
    private val state = MutableStateFlow(LinkDeviceState())
    val stateFlow = state.asStateFlow()

    private val event = Channel<LinkDeviceEvent>()
    val eventFlow = event.receiveAsFlow().take(1)
    private val qrErrors = Channel<Throwable>()
    val qrErrorsFlow = qrErrors.receiveAsFlow()
        .debounce(QR_ERROR_TIME)
        .takeWhile { event.isEmpty }
        .mapNotNull { application.getString(R.string.qrNotRecoveryPassword) }

    private val codec by lazy { MnemonicCodec { MnemonicUtilities.loadFileContents(getApplication(), it) } }

    fun onContinue() {
        viewModelScope.launch {
            runDecodeCatching(state.value.recoveryPhrase)
                .onSuccess(::onSuccess)
                .onFailure(::onFailure)
        }
    }

    fun scan(string: String) {
        viewModelScope.launch {
            runDecodeCatching(string)
                .onSuccess(::onSuccess)
                .onFailure(::onScanFailure)
        }
    }

    fun onChange(recoveryPhrase: String) {
        state.value = LinkDeviceState(recoveryPhrase)
    }
    private fun onSuccess(seed: ByteArray) {
        viewModelScope.launch { event.send(LinkDeviceEvent(seed)) }
    }

    private fun onFailure(error: Throwable) {
        state.update {
            it.copy(
                error = when (error) {
                    is InputTooShort -> R.string.recoveryPasswordErrorMessageShort
                    is InvalidWord -> R.string.recoveryPasswordErrorMessageIncorrect
                    else -> R.string.recoveryPasswordErrorMessageGeneric
                }.let(application::getString)
            )
        }
    }

    private fun onScanFailure(error: Throwable) {
        viewModelScope.launch { qrErrors.send(error) }
    }

    private fun runDecodeCatching(mnemonic: String) = runCatching {
        decode(mnemonic)
    }
    private fun decode(mnemonic: String) = codec.decode(mnemonic).let(Hex::fromStringCondensed)!!
}
