package org.thoughtcrime.securesms.conversation.start.newmessage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.utilities.PublicKeyValidation
import org.session.libsignal.utilities.timeout
import org.thoughtcrime.securesms.ui.GetString
import java.util.concurrent.TimeoutException
import javax.inject.Inject

@HiltViewModel
internal class NewMessageViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application), Callbacks {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _success = MutableSharedFlow<Success>()
    val success get() = _success.asSharedFlow()

    private val _qrErrors = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val qrErrors = _qrErrors.asSharedFlow()

    private var loadOnsJob: Job? = null

    override fun onChange(value: String) {
        loadOnsJob?.cancel()
        loadOnsJob = null

        _state.update { it.copy(newMessageIdOrOns = value, isTextErrorColor = false, loading = false) }
    }

    override fun onContinue() {
        val idOrONS = state.value.newMessageIdOrOns.trim()

        if (PublicKeyValidation.isValid(idOrONS, isPrefixRequired = false)) {
            onUnvalidatedPublicKey(publicKey = idOrONS)
        } else {
            resolveONS(ons = idOrONS)
        }
    }

    override fun onScanQrCode(value: String) {
        if (PublicKeyValidation.isValid(value, isPrefixRequired = false) && PublicKeyValidation.hasValidPrefix(value)) {
            onPublicKey(value)
        } else {
            _qrErrors.tryEmit(application.getString(R.string.this_qr_code_does_not_contain_an_account_id))
        }
    }

    private fun resolveONS(ons: String) {
        if (loadOnsJob?.isActive == true) return

        // This could be an ONS name
        _state.update { it.copy(isTextErrorColor = false, error = null, loading = true) }

        loadOnsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val publicKey = SnodeAPI.getAccountID(ons).timeout(30_000).get()
                if (isActive) onPublicKey(publicKey)
            } catch (e: Exception) {
                if (isActive) onError(e)
            }
        }
    }

    private fun onError(e: Exception) {
        _state.update { it.copy(loading = false, isTextErrorColor = true, error = GetString(e) { it.toMessage() }) }
    }

    private fun onPublicKey(publicKey: String) {
        _state.update { it.copy(loading = false) }
        viewModelScope.launch { _success.emit(Success(publicKey)) }
    }

    private fun onUnvalidatedPublicKey(publicKey: String) {
        if (PublicKeyValidation.hasValidPrefix(publicKey)) {
            onPublicKey(publicKey)
        } else {
            _state.update { it.copy(isTextErrorColor = true, error = GetString(R.string.accountIdErrorInvalid), loading = false) }
        }
    }

    private fun Exception.toMessage() = when (this) {
        is SnodeAPI.Error.Generic -> application.getString(R.string.onsErrorNotRecognized)
        is TimeoutException -> application.getString(R.string.onsErrorUnableToSearch)
        else -> application.getString(R.string.fragment_enter_public_key_error_message)
    }
}

internal data class State(
    val newMessageIdOrOns: String = "",
    val isTextErrorColor: Boolean = false,
    val error: GetString? = null,
    val loading: Boolean = false
) {
    val isNextButtonEnabled: Boolean get() = newMessageIdOrOns.isNotBlank()
}

internal data class Success(val publicKey: String)
