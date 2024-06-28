package org.thoughtcrime.securesms.onboarding.pickname

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.AppTextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.onboarding.messagenotifications.MessageNotificationsActivity
import org.thoughtcrime.securesms.onboarding.messagenotifications.startMessageNotificationsActivity
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.outlinedTextFieldColors
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import javax.inject.Inject

private const val EXTRA_PICK_NEW_NAME = "extra_pick_new_name"

@AndroidEntryPoint
class PickDisplayNameActivity : BaseActionBarActivity() {

    @Inject
    lateinit var viewModelFactory: PickDisplayNameViewModel.AssistedFactory

    private val viewModel: PickDisplayNameViewModel by viewModels {
        val pickNewName = intent.getBooleanExtra(EXTRA_PICK_NEW_NAME, false)
        viewModelFactory.create(pickNewName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()

        ComposeView(this)
            .apply { setContent { DisplayNameScreen(viewModel) } }
            .let(::setContentView)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                startMessageNotificationsActivity()
            }
        }
    }

    @Composable
    private fun DisplayNameScreen(viewModel: PickDisplayNameViewModel) {
        val state = viewModel.stateFlow.collectAsState()

        AppTheme {
            DisplayName(state.value, viewModel::onChange) { viewModel.onContinue(this) }
        }
    }

    @Preview
    @Composable
    fun PreviewDisplayName() {
        PreviewTheme(R.style.Classic_Dark) {
            DisplayName(State())
        }
    }

    @Composable
    fun DisplayName(state: State, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(horizontal = 50.dp)
                .padding(bottom = 12.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(stringResource(state.title), style = MaterialTheme.typography.h4)
            Text(
                stringResource(state.description),
                style = MaterialTheme.typography.base,
                modifier = Modifier.padding(bottom = 12.dp))

            OutlinedTextField(
                value = state.displayName,
                modifier = Modifier.contentDescription(R.string.AccessibilityId_enter_display_name),
                onValueChange = { onChange(it) },
                placeholder = { Text(stringResource(R.string.displayNameEnter)) },
                colors = outlinedTextFieldColors(state.error != null),
                singleLine = true,
                keyboardActions = KeyboardActions(
                    onDone = { onContinue() },
                    onGo = { onContinue() },
                    onSearch = { onContinue() },
                    onSend = { onContinue() },
                ),
                isError = state.error != null,
                shape = RoundedCornerShape(12.dp)
            )

            state.error?.let {
                Text(stringResource(it), style = MaterialTheme.typography.baseBold, color = MaterialTheme.colors.error)
            }

            Spacer(modifier = Modifier.weight(2f))

            OutlineButton(
                stringResource(R.string.continue_2),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(262.dp)
            ) { onContinue() }
        }
    }
}

fun Context.startPickDisplayNameActivity(failedToLoad: Boolean = false, flags: Int = 0) {
    ApplicationContext.getInstance(this).newAccount = !failedToLoad

    Intent(this, PickDisplayNameActivity::class.java)
        .apply { putExtra(EXTRA_PICK_NEW_NAME, failedToLoad) }
        .also { it.flags = flags }
        .also(::startActivity)
}
