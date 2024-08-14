package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun AppBarPreview(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppBar(title = "No back button")
            Divider()
            AppBar(title = "Simple", onBack = {})
            Divider()
            AppBar(
                title = "Action mode",
                onBack = {},
                actionMode = true, actionModeActions = {
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(id = R.drawable.check),
                            contentDescription = "check"
                        )
                    }
                })
        }
    }
}


@ExperimentalMaterial3Api
@Composable
fun AppBar(
    title: String,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actionMode: Boolean = false,
    actionModeActions: @Composable (RowScope.() -> Unit) = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            if (!actionMode) {
                Text(text = title, style = LocalType.current.h4)
            }
        },
        navigationIcon = {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_prev),
                        contentDescription = "back"
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        actions = {
            if (actionMode) {
                actionModeActions()
            } else {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_x),
                        contentDescription = "close"
                    )
                }
            }
        },
    )
}
