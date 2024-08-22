package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
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
        Column() {
            BasicAppBar(title = "Basic App Bar")
            Divider()
            BasicAppBar(
                title = "Basic App Bar With Color",
                backgroundColor = LocalColors.current.backgroundSecondary
            )
            Divider()
            BackAppBar(title = "Back Bar", onBack = {})
            Divider()
            ActionAppBar(
                title = "Action mode",
                actionMode = true,
                actionModeActions = {
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

/**
 * Basic structure for an app bar.
 * It can be passed navigation content and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    backgroundColor: Color = LocalColors.current.background,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            AppBarText(title = title)
        },
        colors = appBarColors(backgroundColor),
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior
    )
}

/**
 * Common use case of an app bar with a back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    backgroundColor: Color = LocalColors.current.background,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BasicAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = {
            AppBarBackIcon(onBack = onBack)
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        backgroundColor = backgroundColor
    )
}

@ExperimentalMaterial3Api
@Composable
fun ActionAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    backgroundColor: Color = LocalColors.current.background,
    actionMode: Boolean = false,
    actionModeTitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    actionModeActions: @Composable (RowScope.() -> Unit) = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            if (!actionMode) {
                AppBarText(title = title)
            }
        },
        navigationIcon = {
            if (actionMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigationIcon()
                    AppBarText(title = actionModeTitle)
                }
            } else {
                navigationIcon()
            }
        },
        scrollBehavior = scrollBehavior,
        colors = appBarColors(backgroundColor),
        actions = {
            if (actionMode) {
                actionModeActions()
            } else {
                actions()
            }
        }
    )
}

@Composable
fun AppBarText(title: String) {
    Text(text = title, style = LocalType.current.h4)
}

@Composable
fun AppBarBackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_left),
            contentDescription = stringResource(R.string.back)
        )
    }
}

@Composable
fun AppBarCloseIcon(onClose: () -> Unit) {
    IconButton(onClick = onClose) {
        Icon(
            painter = painterResource(id = R.drawable.ic_x),
            contentDescription = stringResource(id = R.string.close)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors(backgroundColor: Color) = TopAppBarDefaults.centerAlignedTopAppBarColors()
    .copy(
        containerColor = backgroundColor,
        scrolledContainerColor = backgroundColor,
        navigationIconContentColor = LocalColors.current.text,
        titleContentColor = LocalColors.current.text,
        actionIconContentColor = LocalColors.current.text
    )
