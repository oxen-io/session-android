package org.thoughtcrime.securesms.groups

import android.os.Bundle
import androidx.activity.compose.setContent
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.navigation.dependency
import dagger.hilt.android.AndroidEntryPoint
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.groups.compose.EditGroupInviteViewModel
import org.thoughtcrime.securesms.groups.compose.EditGroupViewModel
import org.thoughtcrime.securesms.groups.destinations.EditClosedGroupScreenDestination
import org.thoughtcrime.securesms.ui.AppTheme
import javax.inject.Inject

@AndroidEntryPoint
class EditClosedGroupActivity: PassphraseRequiredActionBarActivity() {

    companion object {
        const val groupIDKey = "EditClosedGroupActivity_groupID"
    }

    @Inject lateinit var editFactory: EditGroupViewModel.Factory
    @Inject lateinit var inviteFactory: EditGroupInviteViewModel.Factory

    private fun onFinish() {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        setContent {

            AppTheme {
                DestinationsNavHost(
                    navGraph = NavGraphs.editGroup,
                    dependenciesContainerBuilder = {
                        dependency(NavGraphs.editGroup) {
                            editFactory.create(intent.getStringExtra(groupIDKey)!!)
                        }
                        dependency(NavGraphs.editGroup) {
                            inviteFactory.create(intent.getStringExtra(groupIDKey)!!)
                        }
                        dependency(EditClosedGroupScreenDestination) {
                            ::onFinish
                        }
                    }
                )
            }
        }
    }
}