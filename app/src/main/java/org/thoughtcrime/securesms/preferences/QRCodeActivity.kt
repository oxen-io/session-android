package org.thoughtcrime.securesms.preferences

import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import network.loki.messenger.R
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.threadDatabase
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.components.QRScannerScreen
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.util.start

private val TITLES = listOf(R.string.view, R.string.scan)

class QRCodeActivity : PassphraseRequiredActionBarActivity() {

    private val errors = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        supportActionBar!!.title = resources.getString(R.string.qrCode)

        setComposeContent {
            Tabs(
                TextSecurePreferences.getLocalNumber(this)!!,
                errors.asSharedFlow(),
                onScan = ::onScan
            )
        }
    }

    private fun onScan(string: String) {
        if (!PublicKeyValidation.isValid(string)) {
            errors.tryEmit(getString(R.string.qrNotAccountId))
        } else if (!isFinishing) {
            val recipient = Recipient.from(this, Address.fromSerialized(string), false)
            start<ConversationActivityV2> {
                putExtra(ConversationActivityV2.ADDRESS, recipient.address)
                setDataAndType(intent.data, intent.type)
                val existingThread = threadDatabase().getThreadIdIfExistsFor(recipient)
                putExtra(ConversationActivityV2.THREAD_ID, existingThread)
            }
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tabs(accountId: String, errors: Flow<String>, onScan: (String) -> Unit) {
    val pagerState = rememberPagerState { TITLES.size }

    Column {
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (TITLES[page]) {
                R.string.view -> QrPage(accountId)
                R.string.scan -> QRScannerScreen(errors, onScan = onScan)
            }
        }
    }
}

@Composable
fun QrPage(string: String) {
    Column(
        modifier = Modifier
            .background(LocalColors.current.background)
            .padding(horizontal = LocalDimensions.current.mediumSpacing)
            .fillMaxSize()
    ) {
        QrImage(
            string = string,
            modifier = Modifier
                .padding(top = LocalDimensions.current.mediumSpacing, bottom = LocalDimensions.current.xsSpacing)
                .contentDescription(R.string.AccessibilityId_qrCode),
            icon = R.drawable.session
        )

        Text(
            text = stringResource(R.string.accountIdYoursDescription),
            color = LocalColors.current.textSecondary,
            textAlign = TextAlign.Center,
            style = LocalType.current.small
        )
    }
}
