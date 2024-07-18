package org.thoughtcrime.securesms.preferences

import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
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
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.ui.small
import org.thoughtcrime.securesms.util.start

private val TITLES = listOf(R.string.view, R.string.scan)

class QRCodeActivity : PassphraseRequiredActionBarActivity() {

    private val errors = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        supportActionBar!!.title = resources.getString(R.string.activity_qr_code_title)

        setComposeContent {
            Tabs(
                TextSecurePreferences.getLocalNumber(this)!!,
                errors.asSharedFlow(),
                onScan = ::onScan
            )
        }
    }

    fun onScan(string: String) {
        if (!PublicKeyValidation.isValid(string)) {
            errors.tryEmit(getString(R.string.this_qr_code_does_not_contain_an_account_id))
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
                R.string.scan -> MaybeScanQrCode(errors, onScan = onScan)
            }
        }
    }
}

@Composable
fun QrPage(string: String) {
    Column(
        modifier = Modifier
            .background(LocalColors.current.backgroundSecondary)
            .padding(horizontal = LocalDimensions.current.margin)
            .fillMaxSize()
    ) {
        QrImage(
            string = string,
            modifier = Modifier
                .padding(top = LocalDimensions.current.margin, bottom = LocalDimensions.current.xxsMargin)
                .contentDescription(R.string.AccessibilityId_qr_code),
            icon = R.drawable.session
        )

        Text(
            text = stringResource(R.string.this_is_your_account_id_other_users_can_scan_it_to_start_a_conversation_with_you),
            color = LocalColors.current.textSecondary,
            textAlign = TextAlign.Center,
            style = small
        )
    }
}
