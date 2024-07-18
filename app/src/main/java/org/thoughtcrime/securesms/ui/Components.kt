package org.thoughtcrime.securesms.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ButtonColors
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.runIf
import org.thoughtcrime.securesms.components.ProfilePictureView
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.OptionsCard
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.divider
import org.thoughtcrime.securesms.ui.color.radioButtonColors
import org.thoughtcrime.securesms.ui.color.transparentButtonColors
import org.thoughtcrime.securesms.ui.components.SmallCircularProgressIndicator
import kotlin.math.min
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
fun <T> OptionsCard(card: OptionsCard<T>, callbacks: Callbacks<T>) {
    Text(
        card.title(),
        style = base
    )
    CellNoMargin {
        LazyColumn(
            modifier = Modifier.heightIn(max = 5000.dp)
        ) {
            itemsIndexed(card.options) { i, it ->
                if (i != 0) Divider()
                TitledRadioButton(it) { callbacks.setValue(it.value) }
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
    ItemButtonWithDrawable(textId, icon, modifier.heightIn(min = LocalDimensions.current.minLargeItemButtonHeight), h8, colors, onClick)
}

@Composable
fun ItemButtonWithDrawable(
    @StringRes textId: Int,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = xl,
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
    ItemButton(textId, icon, modifier.heightIn(min = LocalDimensions.current.minLargeItemButtonHeight), h8, colors, onClick)
}

/**
 * Courtesy [ItemButton] implementation that takes a [DrawableRes] for the [icon]
 */
@Composable
fun ItemButton(
    @StringRes textId: Int,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = xl,
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
    textStyle: TextStyle = xl,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        onClick = onClick,
        shape = RectangleShape,
    ) {
        Box(
            modifier = Modifier
                .width(80.dp)
                .wrapContentHeight()
                .align(Alignment.CenterVertically)
        ) {
            icon()
        }
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .padding(vertical = LocalDimensions.current.xsItemSpacing)
                .align(Alignment.CenterVertically),
            style = textStyle
        )
    }
}

@Composable
fun Cell(
    padding: Dp = 0.dp,
    margin: Dp = LocalDimensions.current.margin,
    content: @Composable () -> Unit
) {
    CellWithPaddingAndMargin(padding, margin) { content() }
}
@Composable
fun CellNoMargin(content: @Composable () -> Unit) {
    CellWithPaddingAndMargin(padding = 0.dp, margin = 0.dp) { content() }
}

@Composable
fun CellWithPaddingAndMargin(
    padding: Dp = LocalDimensions.current.smallMargin,
    margin: Dp = LocalDimensions.current.margin,
    content: @Composable () -> Unit
) {
    Card(
        backgroundColor = LocalColors.current.backgroundSecondary,
        shape = MaterialTheme.shapes.medium,
        elevation = 0.dp,
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(horizontal = margin),
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun <T> TitledRadioButton(option: RadioOption<T>, onClick: () -> Unit) {
    val color = if (option.enabled) LocalColors.current.text else LocalColors.current.disabled
    Row(
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallItemSpacing),
        modifier = Modifier
            .runIf(option.enabled) { clickable { if (!option.selected) onClick() } }
            .heightIn(min = 60.dp)
            .padding(horizontal = LocalDimensions.current.margin)
            .contentDescription(option.contentDescription)
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically)) {
            Column {
                Text(
                    text = option.title(),
                    style = large,
                    color = color
                )
                option.subtitle?.let {
                    Text(
                        text = it(),
                        style = extraSmall,
                        color = color
                    )
                }
            }
        }
        RadioButton(
            selected = option.selected,
            onClick = null,
            modifier = Modifier
                .height(26.dp)
                .align(Alignment.CenterVertically),
            enabled = option.enabled,
            colors = LocalColors.current.radioButtonColors()
        )
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

fun Modifier.fadingEdges(
    scrollState: ScrollState,
    topEdgeHeight: Dp = 0.dp,
    bottomEdgeHeight: Dp = 20.dp
): Modifier = this.then(
    Modifier
        // adding layer fixes issue with blending gradient and content
        .graphicsLayer { alpha = 0.99F }
        .drawWithContent {
            drawContent()

            val topColors = listOf(Color.Transparent, Color.Black)
            val topStartY = scrollState.value.toFloat()
            val topGradientHeight = min(topEdgeHeight.toPx(), topStartY)
            if (topGradientHeight > 0f) drawRect(
                brush = Brush.verticalGradient(
                    colors = topColors,
                    startY = topStartY,
                    endY = topStartY + topGradientHeight
                ),
                blendMode = BlendMode.DstIn
            )

            val bottomColors = listOf(Color.Black, Color.Transparent)
            val bottomEndY = size.height - scrollState.maxValue + scrollState.value
            val bottomGradientHeight =
                min(bottomEdgeHeight.toPx(), scrollState.maxValue.toFloat() - scrollState.value)
            if (bottomGradientHeight > 0f) drawRect(
                brush = Brush.verticalGradient(
                    colors = bottomColors,
                    startY = bottomEndY - bottomGradientHeight,
                    endY = bottomEndY
                ),
                blendMode = BlendMode.DstIn
            )
        }
)

@Composable
fun Divider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    androidx.compose.material.Divider(
        modifier = modifier.padding(horizontal = LocalDimensions.current.xsMargin),
        color = LocalColors.current.divider,
        startIndent = startIndent
    )
}

@Composable
fun RowScope.Avatar(recipient: Recipient) {
    Box(
        modifier = Modifier
            .width(60.dp)
            .align(Alignment.CenterVertically)
    ) {
        AndroidView(
            factory = {
                ProfilePictureView(it).apply { update(recipient) }
            },
            modifier = Modifier
                .width(46.dp)
                .height(46.dp)
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
            style = h2
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
