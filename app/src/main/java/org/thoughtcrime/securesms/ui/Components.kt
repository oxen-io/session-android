package org.thoughtcrime.securesms.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.components.ProfilePictureView
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.OptionsCardData
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.SmallCircularProgressIndicator
import org.thoughtcrime.securesms.ui.components.TitledRadioButton
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.transparentButtonColors
import kotlin.math.roundToInt

interface Callbacks<in T> {
    fun onSetClick(): Any?
    fun setValue(value: T)
}

object NoOpCallbacks: Callbacks<Any> {
    override fun onSetClick() {}
    override fun setValue(value: Any) {}
}

data class RadioOption<T>(
    val value: T,
    val title: GetString,
    val subtitle: GetString? = null,
    val contentDescription: GetString = title,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun <T> OptionsCard(card: OptionsCardData<T>, callbacks: Callbacks<T>) {
    Column {
        Text(
            modifier = Modifier.padding(start = LocalDimensions.current.smallSpacing),
            text = card.title(),
            style = LocalType.current.base,
            color = LocalColors.current.textSecondary
        )

        Spacer(modifier = Modifier.height(LocalDimensions.current.xsSpacing))

        Cell {
            LazyColumn(
                modifier = Modifier.heightIn(max = 5000.dp)
            ) {
                itemsIndexed(card.options) { i, it ->
                    if (i != 0) Divider()
                    TitledRadioButton(option = it) { callbacks.setValue(it.value) }
                }
            }
        }
    }
}

@Composable
fun LargeItemButtonWithDrawable(
    @StringRes textId: Int,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    ItemButtonWithDrawable(
        textId, icon, modifier,
        LocalType.current.h8, colors, onClick
    )
}

@Composable
fun ItemButtonWithDrawable(
    @StringRes textId: Int,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalType.current.xl,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    val context = LocalContext.current

    ItemButton(
        text = stringResource(textId),
        modifier = modifier,
        icon = {
            Image(
                painter = rememberDrawablePainter(drawable = AppCompatResources.getDrawable(context, icon)),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center)
            )
        },
        textStyle = textStyle,
        colors = colors,
        onClick = onClick
    )
}

@Composable
fun LargeItemButton(
    @StringRes textId: Int,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    ItemButton(
        textId = textId,
        icon = icon,
        modifier = modifier,
        minHeight = LocalDimensions.current.minLargeItemButtonHeight,
        textStyle = LocalType.current.h8,
        colors = colors,
        onClick = onClick
    )
}

@Composable
fun LargeItemButton(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    ItemButton(
        text = text,
        icon = icon,
        modifier = modifier,
        minHeight = LocalDimensions.current.minLargeItemButtonHeight,
        textStyle = LocalType.current.h8,
        colors = colors,
        onClick = onClick
    )
}

@Composable
fun ItemButton(
    text: String,
    icon: Int,
    modifier: Modifier,
    minHeight: Dp = LocalDimensions.current.minItemButtonHeight,
    textStyle: TextStyle = LocalType.current.xl,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    ItemButton(
        text = text,
        modifier = modifier,
        icon = {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center)
            )
        },
        minHeight = minHeight,
        textStyle = textStyle,
        colors = colors,
        onClick = onClick
    )
}

/**
 * Courtesy [ItemButton] implementation that takes a [DrawableRes] for the [icon]
 */
@Composable
fun ItemButton(
    @StringRes textId: Int,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    minHeight: Dp = LocalDimensions.current.minItemButtonHeight,
    textStyle: TextStyle = LocalType.current.xl,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    ItemButton(
        text = stringResource(textId),
        modifier = modifier,
        icon = {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center)
            )
        },
        minHeight = minHeight,
        textStyle = textStyle,
        colors = colors,
        onClick = onClick
    )
}

/**
* Base [ItemButton] implementation.
 *
 * A button to be used in a list of buttons, usually in a [Cell] or [Card]
*/
@Composable
fun ItemButton(
    text: String,
    icon: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = LocalDimensions.current.minLargeItemButtonHeight,
    textStyle: TextStyle = LocalType.current.xl,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        onClick = onClick,
        contentPadding = PaddingValues(),
        shape = RectangleShape,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = LocalDimensions.current.xxsSpacing)
                .size(minHeight)
                .align(Alignment.CenterVertically),
            content = icon
        )

        Text(
            text,
            Modifier
                .fillMaxWidth()
                .align(Alignment.CenterVertically),
            style = textStyle
        )
    }
}

@Preview
@Composable
fun PreviewItemButton() {
    PreviewTheme {
        ItemButton(
            textId = R.string.groupCreate,
            icon = R.drawable.ic_group,
            onClick = {}
        )
    }
}

@Preview
@Composable
fun PreviewLargeItemButton() {
    PreviewTheme {
        LargeItemButton(
            textId = R.string.groupCreate,
            icon = R.drawable.ic_group,
            onClick = {}
        )
    }
}

@Composable
fun Cell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = LocalColors.current.backgroundSecondary,
                shape = MaterialTheme.shapes.small
            )
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
fun Modifier.contentDescription(text: GetString?): Modifier {
    return text?.let {
        val context = LocalContext.current
        semantics { contentDescription = it(context) }
    } ?: this
}

@Composable
fun Modifier.contentDescription(@StringRes id: Int?): Modifier {
    val context = LocalContext.current
    return id?.let { semantics { contentDescription = context.getString(it) } } ?: this
}

@Composable
fun Modifier.contentDescription(text: String?): Modifier {
    return text?.let { semantics { contentDescription = it } } ?: this
}

@Composable
fun BottomFadingEdgeBox(
    modifier: Modifier = Modifier,
    fadingEdgeHeight: Dp = LocalDimensions.current.spacing,
    fadingColor: Color = LocalColors.current.background,
    content: @Composable BoxScope.(bottomContentPadding: Dp) -> Unit,
) {
    Box(modifier) {
        this.content(fadingEdgeHeight)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(fadingEdgeHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to fadingColor,
                        tileMode = TileMode.Repeated
                    )
                )
        )
    }
}

@Preview
@Composable
private fun BottomFadingEdgeBoxPreview() {
    Column(modifier = Modifier.background(LocalColors.current.background)) {
        BottomFadingEdgeBox(
            modifier = Modifier
                .height(600.dp)
                .background(LocalColors.current.backgroundSecondary),
            content = { bottomContentPadding ->
                LazyColumn(contentPadding = PaddingValues(bottom = bottomContentPadding)) {
                    items(200) {
                        Text("Item $it",
                            color = LocalColors.current.text,
                            style = LocalType.current.base)
                    }
                }
            },
        )

        PrimaryOutlineButton(
            modifier = Modifier
                .align(Alignment.CenterHorizontally),
            text = "Do stuff", onClick = {}
        )
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    HorizontalDivider(
        modifier = modifier
            .padding(horizontal = LocalDimensions.current.smallSpacing)
            .padding(start = startIndent),
        color = LocalColors.current.borders,
    )
}

//TODO This component should be fully rebuilt in Compose at some point ~~
@Composable
fun Avatar(
    recipient: Recipient,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = {
            ProfilePictureView(it).apply { update(recipient) }
        },
        modifier = modifier
    )
}

@Composable
fun Avatar(
    userAddress: Address,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        Image(
            painterResource(id = R.drawable.ic_profile_default),
            colorFilter = ColorFilter.tint(LocalColors.current.textSecondary),
            contentScale = ContentScale.Inside,
            contentDescription = null,
            modifier = Modifier
                .size(LocalDimensions.current.iconLarge)
                .clip(CircleShape)
                .border(1.dp, LocalColors.current.borders, CircleShape)
        )
    } else {
        AndroidView(
            factory = {
                ProfilePictureView(it).apply { update(userAddress) }
            },
            modifier = modifier
        )
    }
}

@Composable
fun ProgressArc(progress: Float, modifier: Modifier = Modifier) {
    val text = (progress * 100).roundToInt()

    Box(modifier = modifier) {
        Arc(percentage = progress, modifier = Modifier.align(Alignment.Center))
        Text(
            "${text}%",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
            style = LocalType.current.h2
        )
    }
}

@Composable
fun Arc(
    modifier: Modifier = Modifier,
    percentage: Float = 0.25f,
    fillColor: Color = LocalColors.current.primary,
    backgroundColor: Color = LocalColors.current.borders,
    strokeWidth: Dp = 18.dp,
    sweepAngle: Float = 310f,
    startAngle: Float = (360f - sweepAngle) / 2 + 90f
) {
    Canvas(
        modifier = modifier
            .padding(strokeWidth)
            .size(186.dp)
    ) {
        // Background Line
        drawArc(
            color = backgroundColor,
            startAngle,
            sweepAngle,
            false,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )

        drawArc(
            color = fillColor,
            startAngle,
            percentage * sweepAngle,
            false,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )
    }
}

@Composable
fun RowScope.SessionShieldIcon() {
    Icon(
        painter = painterResource(R.drawable.session_shield),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.CenterVertically)
            .wrapContentSize(unbounded = true)
    )
}

@Composable
fun LaunchedEffectAsync(block: suspend CoroutineScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scope.launch(Dispatchers.IO) { block() } }
}

@Composable
fun LoadingArcOr(loading: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(loading) {
        SmallCircularProgressIndicator(color = LocalContentColor.current)
    }
    AnimatedVisibility(!loading) {
        content()
    }
}


@Composable
fun SearchBar(
    query: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    backgroundColor: Color = LocalColors.current.background
) {
    BasicTextField(
        singleLine = true,
        value = query,
        onValueChange = onValueChanged,
        enabled = enabled,
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(100))
            ) {
                Image(
                    painterResource(id = R.drawable.ic_search_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        LocalColors.current.textSecondary
                    ),
                    modifier = Modifier
                        .padding(
                            horizontal = LocalDimensions.current.smallSpacing,
                            vertical = LocalDimensions.current.xxsSpacing
                        )
                        .size(LocalDimensions.current.iconMedium)
                )

                Box(modifier = Modifier.weight(1f)) {
                    innerTextField()
                    if (query.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = LocalColors.current.textSecondary,
                            style = LocalType.current.xl
                        )
                    }
                }
            }
        },
        textStyle = LocalType.current.base.copy(color = LocalColors.current.text),
        modifier = modifier,
        cursorBrush = SolidColor(LocalColors.current.text)
    )
}