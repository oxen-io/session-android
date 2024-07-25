package org.thoughtcrime.securesms.groups.compose

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.groups.CreateGroupViewModel
import org.thoughtcrime.securesms.groups.ViewState

@Composable
fun CreateGroupScreen(
    viewModel: CreateGroupViewModel,
    onFinishCreation: () -> Unit,
    onCancelCreation: () -> Unit,
) {
    val viewState by viewModel.viewState.observeAsState(ViewState.DEFAULT)
    val navController = rememberNavController()

    NavHost(navController, startDestination = RouteCreateGroup) {
        composable<RouteCreateGroup> {
            CreateGroup(
                viewState = viewState,
                updateState = viewModel::updateState,
                onClose = onCancelCreation,
                onSelectContact = { navController.navigate(RouteSelectContacts(viewModel.availableContactAccountIDsToSelect)) },
                onBack = onCancelCreation
            )
        }

        composable<RouteSelectContacts> { entry ->
            val route: RouteSelectContacts = entry.toRoute()

            SelectContactsScreen(
                onlySelectFromAccountIDs = route.onlySelectFromAccountIDs?.toSet(),
                onDoneClicked = viewModel::onContactsSelected,
                onBackClicked = navController::popBackStack
            )
        }

    }

    // Launch the conversation activity when the group is created
    val context = LocalContext.current
    LaunchedEffect(viewState.createdGroup != null) {
        viewState.createdGroup?.let { group ->
            onFinishCreation()
            val intent = Intent(context, ConversationActivityV2::class.java).apply {
                putExtra(ConversationActivityV2.ADDRESS, group.address)
            }
            context.startActivity(intent)
        }
    }
}