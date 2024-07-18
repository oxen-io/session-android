package org.thoughtcrime.securesms.recoverypassword

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.setComposeContent

class RecoveryPasswordActivity : BaseActionBarActivity() {

    private val viewModel: RecoveryPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.title = resources.getString(R.string.sessionRecoveryPassword)

        setComposeContent {
            val mnemonic by viewModel.mnemonic.collectAsState("")
            val seed by viewModel.seed.collectAsState(null)

            RecoveryPasswordScreen(
                mnemonic = mnemonic,
                seed = seed,
                copyMnemonic = viewModel::copyMnemonic,
                onHide = ::onHide
            )
        }
    }

    private fun onHide() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            htmlText(R.string.recoveryPasswordHidePermanentlyDescription1)
            destructiveButton(R.string.continue_2, R.string.AccessibilityId_continue) { onHideConfirm() }
            cancelButton()
        }
    }

    private fun onHideConfirm() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            text(R.string.recoveryPasswordHidePermanentlyDescription2)
            cancelButton()
            destructiveButton(
                R.string.yes,
                contentDescription = R.string.AccessibilityId_confirm_button
            ) {
                viewModel.permanentlyHidePassword()
                finish()
            }
        }
    }
}
