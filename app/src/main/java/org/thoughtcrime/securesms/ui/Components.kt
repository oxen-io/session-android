package org.thoughtcrime.securesms.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Colors
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.runIf
import org.thoughtcrime.securesms.components.ProfilePictureView
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.OptionsCard
import kotlin.math.min

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
    Text(text = card.title())
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
fun ItemButton(
    text: String,
    @DrawableRes icon: Int,
    colors: ButtonColors = transparentButtonColors(),
    contentDescription: String = text,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = colors,
        onClick = onClick,
        shape = RectangleShape,
    ) {
        Box(modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = contentDescription,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(text, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun Cell(content: @Composable () -> Unit) {
    CellWithPaddingAndMargin(padding = 0.dp) { content() }
}
@Composable
fun CellNoMargin(content: @Composable () -> Unit) {
    CellWithPaddingAndMargin(padding = 0.dp, margin = 0.dp) { content() }
}

@Composable
fun CellWithPaddingAndMargin(
    padding: Dp = 24.dp,
    margin: Dp = 32.dp,
    content: @Composable () -> Unit
) {
    Card(
        backgroundColor = MaterialTheme.colors.cellColor,
        shape = RoundedCornerShape(16.dp),
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .runIf(option.enabled) { clickable { if (!option.selected) onClick() } }
            .heightIn(min = 60.dp)
            .padding(horizontal = 32.dp)
            .contentDescription(option.contentDescription)
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically)) {
            Column {
                Text(
                    text = option.title(),
                    fontSize = 16.sp,
                    modifier = Modifier.alpha(if (option.enabled) 1f else 0.5f)
                )
                option.subtitle?.let {
                    Text(
                        text = it(),
                        fontSize = 11.sp,
                        modifier = Modifier.alpha(if (option.enabled) 1f else 0.5f)
                    )
                }
            }
        }
        RadioButton(
            selected = option.selected,
            onClick = null,
            enabled = option.enabled,
            modifier = Modifier
                .height(26.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

@Composable
fun Modifier.contentDescription(text: GetString?): Modifier {
    val context = LocalContext.current
    return text?.let { semantics { contentDescription = it(context) } } ?: this
}

@Composable
fun OutlineButton(text: GetString, contentDescription: GetString? = text, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        modifier = modifier.size(108.dp, 34.dp)
            .contentDescription(contentDescription),
        onClick = onClick,
        border = BorderStroke(1.dp, LocalExtraColors.current.prominentButtonColor),
        shape = RoundedCornerShape(50), // = 50% percent
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = LocalExtraColors.current.prominentButtonColor,
            backgroundColor = MaterialTheme.colors.background
        )
    ){
        Text(text = text())
    }
}

private val Colors.cellColor: Color
    @Composable
    get() = LocalExtraColors.current.settingsBackground

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
fun Divider() {
    androidx.compose.material.Divider(
        modifier = Modifier.padding(horizontal = 16.dp),
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
