package org.thoughtcrime.securesms.conversation.v2

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageContentBinding
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.MediaPreviewActivity.getPreviewIntent
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.ui.Avatar
import org.thoughtcrime.securesms.ui.CarouselNextButton
import org.thoughtcrime.securesms.ui.CarouselPrevButton
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.HorizontalPagerIndicator
import org.thoughtcrime.securesms.ui.LargeItemButton
import org.thoughtcrime.securesms.ui.TitledText
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.blackAlpha40
import org.thoughtcrime.securesms.ui.theme.bold
import org.thoughtcrime.securesms.ui.theme.dangerButtonColors
import org.thoughtcrime.securesms.ui.theme.monospace
import javax.inject.Inject

@AndroidEntryPoint
class MessageDetailActivity : PassphraseRequiredActionBarActivity() {

    @Inject
    lateinit var storage: StorageProtocol

    private val viewModel: MessageDetailsViewModel by viewModels()

    companion object {
        // Extras
        const val MESSAGE_TIMESTAMP = "message_timestamp"

        const val ON_REPLY = 1
        const val ON_RESEND = 2
        const val ON_DELETE = 3
        const val ON_COPY = 4
        const val ON_SAVE = 5
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        title = resources.getString(R.string.messageInfo)

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

        // can only save if the there is a media attachment which has finished downloading.
        val canSave = state.mmsRecord?.containsMediaSlide() == true
                && state.mmsRecord?.isMediaPending == false

        MessageDetails(
            state = state,
            onReply = if (state.canReply) { { setResultAndFinish(ON_REPLY) } } else null,
            onResend = state.error?.let { { setResultAndFinish(ON_RESEND) } },
            onSave = if(canSave) { { setResultAndFinish(ON_SAVE) } } else null,
            onDelete = { setResultAndFinish(ON_DELETE) },
            onCopy = { setResultAndFinish(ON_COPY) },
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
    onSave: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onClickImage: (Int) -> Unit = {},
    onAttachmentNeedsDownload: (DatabaseAttachment) -> Unit = { _ -> }
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = LocalDimensions.current.smallSpacing),
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)
    ) {
        state.record?.let { message ->
            AndroidView(
                modifier = Modifier.padding(horizontal = LocalDimensions.current.spacing),
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
            onReply = onReply,
            onResend = onResend,
            onSave = onSave,
            onDelete = onDelete,
            onCopy = onCopy
        )
    }
}

@Composable
fun CellMetadata(
    state: MessageDetailsState,
) {
    state.apply {
        if (listOfNotNull(sent, received, error, senderInfo).isEmpty()) return
        Cell(modifier = Modifier.padding(horizontal = LocalDimensions.current.spacing)) {
            Column(
                modifier = Modifier.padding(LocalDimensions.current.spacing),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)
            ) {
                TitledText(sent)
                TitledText(received)
                TitledErrorText(error)
                senderInfo?.let {
                    TitledView(state.fromTitle) {
                        Row {
                            sender?.let {
                                Avatar(
                                    recipient = it,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .size(46.dp)
                                )
                                Spacer(modifier = Modifier.width(LocalDimensions.current.smallSpacing))
                            }
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
    onSave: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    Cell(modifier = Modifier.padding(horizontal = LocalDimensions.current.spacing)) {
        Column {
            onReply?.let {
                LargeItemButton(
                    R.string.reply,
                    R.drawable.ic_message_details__reply,
                    onClick = it
                )
                Divider()
            }

            LargeItemButton(
                R.string.copy,
                R.drawable.ic_copy,
                onClick = onCopy
            )
            Divider()

            onSave?.let {
                LargeItemButton(
                    R.string.save,
                    R.drawable.ic_baseline_save_24,
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
                R.drawable.ic_delete,
                colors = dangerButtonColors(),
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

    Column(verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)) {
        Row {
            CarouselPrevButton(pagerState)
            Box(modifier = Modifier.weight(1f)) {
                CarouselPager(pagerState, attachments, onClick)
                HorizontalPagerIndicator(
                    pagerState = pagerState,
                    modifier = Modifier.padding(bottom = LocalDimensions.current.xxsSpacing)
                )
                ExpandButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(LocalDimensions.current.xxsSpacing)
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
private fun CarouselPager(
    pagerState: PagerState,
    attachments: List<Attachment>,
    onClick: (Int) -> Unit
) {
    Cell(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
    ) {
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
            contentDescription = stringResource(id = R.string.AccessibilityId_expand),
            modifier = Modifier.clickable { onClick() },
        )
    }
}

@Preview
@Composable
fun PreviewMessageDetailsButtons(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        CellButtons(
            onReply = {},
            onResend = {},
            onSave = {},
            onDelete = {},
            onCopy = {}
        )
    }
}

@Preview
@Composable
fun PreviewMessageDetails(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        MessageDetails(
            state = MessageDetailsState(
                imageAttachments = listOf(
                    Attachment(
                        fileDetails = listOf(
                            TitledText(R.string.attachmentsFileId, "Screen Shot 2023-07-06 at 11.35.50 am.png")
                        ),
                        fileName = "Screen Shot 2023-07-06 at 11.35.50 am.png",
                        uri = Uri.parse(""),
                        hasImage = true
                    ),
                    Attachment(
                        fileDetails = listOf(
                            TitledText(R.string.attachmentsFileId, "Screen Shot 2023-07-06 at 11.35.50 am.png")
                        ),
                        fileName = "Screen Shot 2023-07-06 at 11.35.50 am.png",
                        uri = Uri.parse(""),
                        hasImage = true
                    ),
                    Attachment(
                        fileDetails = listOf(
                            TitledText(R.string.attachmentsFileId, "Screen Shot 2023-07-06 at 11.35.50 am.png")
                        ),
                        fileName = "Screen Shot 2023-07-06 at 11.35.50 am.png",
                        uri = Uri.parse(""),
                        hasImage = true
                    )

                ),
                nonImageAttachmentFileDetails = listOf(
                    TitledText(R.string.attachmentsFileId, "Screen Shot 2023-07-06 at 11.35.50 am.png"),
                    TitledText(R.string.attachmentsFileType, "image/png"),
                    TitledText(R.string.attachmentsFileSize, "195.6kB"),
                    TitledText(R.string.attachmentsResolution, "342x312"),
                ),
                sent = TitledText(R.string.sent, "6:12 AM Tue, 09/08/2022"),
                received = TitledText(R.string.received, "6:12 AM Tue, 09/08/2022"),
                error = TitledText(R.string.error, "Message failed to send"),
                senderInfo = TitledText("Connor", "d4f1g54sdf5g1d5f4g65ds4564df65f4g65d54"),
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileDetails(fileDetails: List<TitledText>) {
    if (fileDetails.isEmpty()) return

    Cell(modifier = Modifier.padding(horizontal = LocalDimensions.current.spacing)) {
        FlowRow(
            modifier = Modifier.padding(horizontal = LocalDimensions.current.xsSpacing, vertical = LocalDimensions.current.spacing),
            verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)
        ) {
            fileDetails.forEach {
                BoxWithConstraints {
                    TitledText(
                        it,
                        modifier = Modifier
                            .widthIn(min = maxWidth.div(2))
                            .padding(horizontal = LocalDimensions.current.xsSpacing)
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
        style = LocalType.current.base,
        color = LocalColors.current.danger
    )
}

@Composable
fun TitledMonospaceText(titledText: TitledText?) {
    TitledText(
        titledText,
        style = LocalType.current.base.monospace()
    )
}

@Composable
fun TitledText(
    titledText: TitledText?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalType.current.base,
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsSpacing)) {
        Text(title.string(), style = LocalType.current.base.bold())
        content()
    }
}
