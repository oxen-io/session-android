package org.thoughtcrime.securesms.conversation.v2

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent.ACTION_UP
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageContentBinding
import org.thoughtcrime.securesms.MediaPreviewActivity.getPreviewIntent
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.ui.Avatar
import org.thoughtcrime.securesms.ui.CarouselNextButton
import org.thoughtcrime.securesms.ui.CarouselPrevButton
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.CellNoMargin
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.HorizontalPagerIndicator
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.LargeItemButton
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.TitledText
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.baseMonospace
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.blackAlpha40
import org.thoughtcrime.securesms.ui.color.destructiveButtonColors
import org.thoughtcrime.securesms.ui.setComposeContent
import javax.inject.Inject

@AndroidEntryPoint
class MessageDetailActivity : PassphraseRequiredActionBarActivity() {

    @Inject
    lateinit var storage: Storage

    private val viewModel: MessageDetailsViewModel by viewModels()

    companion object {
        // Extras
        const val MESSAGE_TIMESTAMP = "message_timestamp"

        const val ON_REPLY = 1
        const val ON_RESEND = 2
        const val ON_DELETE = 3
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        title = resources.getString(R.string.conversation_context__menu_message_details)

        viewModel.timestamp = intent.getLongExtra(MESSAGE_TIMESTAMP, -1L)

        setComposeContent { MessageDetailsScreen() }

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                when (it) {
                    Event.Finish -> finish()
                    is Event.StartMediaPreview -> startActivity(
                        getPreviewIntent(this@MessageDetailActivity, it.args)
                    )
                }
            }
        }
    }

    @Composable
    private fun MessageDetailsScreen() {
        val state by viewModel.stateFlow.collectAsState()
        MessageDetails(
            state = state,
            onReply = if (state.canReply) { { setResultAndFinish(ON_REPLY) } } else null,
            onResend = state.error?.let { { setResultAndFinish(ON_RESEND) } },
            onDelete = { setResultAndFinish(ON_DELETE) },
            onClickImage = { viewModel.onClickImage(it) },
            onAttachmentNeedsDownload = viewModel::onAttachmentNeedsDownload,
        )
    }

    private fun setResultAndFinish(code: Int) {
        Bundle().apply { putLong(MESSAGE_TIMESTAMP, viewModel.timestamp) }
            .let(Intent()::putExtras)
            .let { setResult(code, it) }

        finish()
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MessageDetails(
    state: MessageDetailsState,
    onReply: (() -> Unit)? = null,
    onResend: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    onClickImage: (Int) -> Unit = {},
    onAttachmentNeedsDownload: (Long, Long) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = LocalDimensions.current.smallItemSpacing),
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallItemSpacing)
    ) {
        state.record?.let { message ->
            AndroidView(
                modifier = Modifier.padding(horizontal = LocalDimensions.current.margin),
                factory = {
                    ViewVisibleMessageContentBinding.inflate(LayoutInflater.from(it)).mainContainerConstraint.apply {
                        bind(
                            message,
                            thread = state.thread!!,
                            onAttachmentNeedsDownload = onAttachmentNeedsDownload,
                            suppressThumbnails = true
                        )

                        setOnTouchListener { _, event ->
                            if (event.actionMasked == ACTION_UP) onContentClick(event)
                            true
                        }
                    }
                }
            )
        }
        Carousel(state.imageAttachments) { onClickImage(it) }
        state.nonImageAttachmentFileDetails?.let { FileDetails(it) }
        CellMetadata(state)
        CellButtons(
            onReply,
            onResend,
            onDelete,
        )
    }
}

@Composable
fun CellMetadata(
    state: MessageDetailsState,
) {
    state.apply {
        if (listOfNotNull(sent, received, error, senderInfo).isEmpty()) return
        CellWithPaddingAndMargin {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallItemSpacing)) {
                TitledText(sent)
                TitledText(received)
                TitledErrorText(error)
                senderInfo?.let {
                    TitledView(state.fromTitle) {
                        Row {
                            sender?.let { Avatar(it) }
                            TitledMonospaceText(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CellButtons(
    onReply: (() -> Unit)? = null,
    onResend: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
) {
    Cell {
        Column {
            onReply?.let {
                LargeItemButton(
                    R.string.reply,
                    R.drawable.ic_message_details__reply,
                    onClick = it
                )
                Divider()
            }
            onResend?.let {
                LargeItemButton(
                    R.string.resend,
                    R.drawable.ic_message_details__refresh,
                    onClick = it
                )
                Divider()
            }
            LargeItemButton(
                R.string.delete,
                R.drawable.ic_message_details__trash,
                colors = destructiveButtonColors(),
                onClick = onDelete
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Carousel(attachments: List<Attachment>, onClick: (Int) -> Unit) {
    if (attachments.isEmpty()) return

    val pagerState = rememberPagerState { attachments.size }

    Column(verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallItemSpacing)) {
        Row {
            CarouselPrevButton(pagerState)
            Box(modifier = Modifier.weight(1f)) {
                CellCarousel(pagerState, attachments, onClick)
                HorizontalPagerIndicator(pagerState)
                ExpandButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(LocalDimensions.current.xxsItemSpacing)
                ) { onClick(pagerState.currentPage) }
            }
            CarouselNextButton(pagerState)
        }
        attachments.getOrNull(pagerState.currentPage)?.fileDetails?.let { FileDetails(it) }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class
)
@Composable
private fun CellCarousel(
    pagerState: PagerState,
    attachments: List<Attachment>,
    onClick: (Int) -> Unit
) {
    CellNoMargin {
        HorizontalPager(state = pagerState) { i ->
            GlideImage(
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onClick(i) },
                model = attachments[i].uri,
                contentDescription = attachments[i].fileName ?: stringResource(id = R.string.image)
            )
        }
    }
}

@Composable
fun ExpandButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = blackAlpha40,
        modifier = modifier,
        contentColor = Color.White,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_expand),
            contentDescription = stringResource(id = R.string.expand),
            modifier = Modifier.clickable { onClick() },
        )
    }
}


@Preview
@Composable
fun PreviewMessageDetails(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        MessageDetails(
            state = MessageDetailsState(
                nonImageAttachmentFileDetails = listOf(
                    TitledText(R.string.message_details_header__file_id, "Screen Shot 2023-07-06 at 11.35.50 am.png"),
                    TitledText(R.string.message_details_header__file_type, "image/png"),
                    TitledText(R.string.message_details_header__file_size, "195.6kB"),
                    TitledText(R.string.message_details_header__resolution, "342x312"),
                ),
                sent = TitledText(R.string.message_details_header__sent, "6:12 AM Tue, 09/08/2022"),
                received = TitledText(R.string.message_details_header__received, "6:12 AM Tue, 09/08/2022"),
                error = TitledText(R.string.message_details_header__error, "Message failed to send"),
                senderInfo = TitledText("Connor", "d4f1g54sdf5g1d5f4g65ds4564df65f4g65d54"),
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileDetails(fileDetails: List<TitledText>) {
    if (fileDetails.isEmpty()) return

    Cell {
        FlowRow(
            modifier = Modifier.padding(horizontal = LocalDimensions.current.xsItemSpacing, vertical = LocalDimensions.current.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallItemSpacing)
        ) {
            fileDetails.forEach {
                BoxWithConstraints {
                    TitledText(
                        it,
                        modifier = Modifier
                            .widthIn(min = maxWidth.div(2))
                            .padding(horizontal = LocalDimensions.current.xsItemSpacing)
                            .width(IntrinsicSize.Max)
                    )
                }
            }
        }
    }
}

@Composable
fun TitledErrorText(titledText: TitledText?) {
    TitledText(
        titledText,
        style = base,
        color = LocalColors.current.danger
    )
}

@Composable
fun TitledMonospaceText(titledText: TitledText?) {
    TitledText(
        titledText,
        style = baseMonospace
    )
}

@Composable
fun TitledText(
    titledText: TitledText?,
    modifier: Modifier = Modifier,
    style: TextStyle = base,
    color: Color = Color.Unspecified
) {
    titledText?.apply {
        TitledView(title, modifier) {
            Text(
                text,
                style = style,
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TitledView(title: GetString, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsItemSpacing)) {
        Text(title.string(), style = baseBold)
        content()
    }
}
