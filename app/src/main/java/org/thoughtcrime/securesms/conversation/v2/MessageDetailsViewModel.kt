package org.thoughtcrime.securesms.conversation.v2

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.Util
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.MediaPreviewArgs
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.TitledText
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MessageDetailsViewModel @Inject constructor(
    private val attachmentDb: AttachmentDatabase,
    private val lokiMessageDatabase: LokiMessageDatabase,
    private val mmsSmsDatabase: MmsSmsDatabase,
    private val threadDb: ThreadDatabase,
    private val repository: ConversationRepository,
) : ViewModel() {

    private var job: Job? = null

    private val state = MutableStateFlow(MessageDetailsState())
    val stateFlow = state.asStateFlow()

    private val event = Channel<Event>()
    val eventFlow = event.receiveAsFlow()

    var timestamp: Long = 0L
        set(value) {
            job?.cancel()

            field = value
            val record = mmsSmsDatabase.getMessageForTimestamp(timestamp)

            if (record == null) {
                viewModelScope.launch { event.send(Event.Finish) }
                return
            }

            val mmsRecord = record as? MmsMessageRecord

            job = viewModelScope.launch {
                repository.changes(record.threadId)
                    .filter { mmsSmsDatabase.getMessageForTimestamp(value) == null }
                    .collect { event.send(Event.Finish) }
            }

            state.value = record.run {
                val slides = mmsRecord?.slideDeck?.slides ?: emptyList()

                MessageDetailsState(
                    attachments = slides.map(::Attachment),
                    record = record,
                    sent = dateSent.let(::Date).toString().let { TitledText(R.string.message_details_header__sent, it) },
                    received = dateReceived.let(::Date).toString().let { TitledText(R.string.message_details_header__received, it) },
                    error = lokiMessageDatabase.getErrorMessage(id)?.let { TitledText(R.string.message_details_header__error, it) },
                    senderInfo = individualRecipient.run { name?.let { TitledText(it, address.serialize()) } },
                    sender = individualRecipient,
                    thread = threadDb.getRecipientForThreadId(threadId)!!,
                )
            }
        }

    private val Slide.details: List<TitledText>
        get() = listOfNotNull(
            fileName.orNull()?.let { TitledText(R.string.message_details_header__file_id, it) },
            TitledText(R.string.message_details_header__file_type, asAttachment().contentType),
            TitledText(R.string.message_details_header__file_size, Util.getPrettyFileSize(fileSize)),
            takeIf { it is ImageSlide }
                ?.let(Slide::asAttachment)
                ?.run { "${width}x$height" }
                ?.let { TitledText(R.string.message_details_header__resolution, it) },
            attachmentDb.duration(this)?.let { TitledText(R.string.message_details_header__duration, it) },
        )

    private fun AttachmentDatabase.duration(slide: Slide): String? =
        slide.takeIf { it.hasAudio() }
            ?.run { asAttachment() as? DatabaseAttachment }
            ?.run { getAttachmentAudioExtras(attachmentId)?.durationMs }
            ?.takeIf { it > 0 }
            ?.let {
                String.format(
                    "%01d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(it),
                    TimeUnit.MILLISECONDS.toSeconds(it) % 60
                )
            }

    fun Attachment(slide: Slide): Attachment =
        Attachment(slide.details, slide.fileName.orNull(), slide.uri, slide is ImageSlide)

    fun onClickImage(index: Int) {
        val state = state.value ?: return
        val mmsRecord = state.mmsRecord ?: return
        val slide = mmsRecord.slideDeck.slides[index] ?: return
        // only open to downloaded images
        if (slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED) {
            // Restart download here (on IO thread)
            (slide.asAttachment() as? DatabaseAttachment)?.let { attachment ->
                onAttachmentNeedsDownload(attachment.attachmentId.rowId, state.mmsRecord.getId())
            }
        }

        if (slide.isInProgress) return

        viewModelScope.launch {
            MediaPreviewArgs(slide, state.mmsRecord, state.thread)
                .let(Event::StartMediaPreview)
                .let { event.send(it) }
        }
    }

    fun onAttachmentNeedsDownload(attachmentId: Long, mmsId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            JobQueue.shared.add(AttachmentDownloadJob(attachmentId, mmsId))
        }
    }
}

data class MessageDetailsState(
    val attachments: List<Attachment> = emptyList(),
    val imageAttachments: List<Attachment> = attachments.filter { it.hasImage },
    val nonImageAttachmentFileDetails: List<TitledText>? = attachments.firstOrNull { !it.hasImage }?.fileDetails,
    val record: MessageRecord? = null,
    val mmsRecord: MmsMessageRecord? = record as? MmsMessageRecord,
    val sent: TitledText? = null,
    val received: TitledText? = null,
    val error: TitledText? = null,
    val senderInfo: TitledText? = null,
    val sender: Recipient? = null,
    val thread: Recipient? = null,
) {
    val fromTitle = GetString(R.string.message_details_header__from)
}

data class Attachment(
    val fileDetails: List<TitledText>,
    val fileName: String?,
    val uri: Uri?,
    val hasImage: Boolean
)

sealed class Event {
    object Finish: Event()
    data class StartMediaPreview(val args: MediaPreviewArgs): Event()
}