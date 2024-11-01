package org.thoughtcrime.securesms.media

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import org.session.libsession.messaging.messages.control.DataExtractionNotification
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.MediaPreviewActivity
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.AttachmentUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SaveAttachmentTask
import org.thoughtcrime.securesms.util.asSequence
import org.thoughtcrime.securesms.util.observeChanges
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MediaOverviewViewModel(
    private val address: Address,
    private val application: Application,
    private val threadDatabase: ThreadDatabase,
    private val mediaDatabase: MediaDatabase
) : AndroidViewModel(application) {
    private val timeBuckets by lazy { FixedTimeBuckets() }
    private val monthTimeBucketFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    private val recipient: SharedFlow<Recipient> = application.contentResolver
        .observeChanges(DatabaseContentProviders.Attachment.CONTENT_URI)
        .onStart { emit(DatabaseContentProviders.Attachment.CONTENT_URI) }
        .map { Recipient.from(application, address, false) }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val title: StateFlow<String> = recipient
        .map { it.toShortString() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val mediaListState: StateFlow<MediaOverviewContent?> = recipient
        .map { recipient ->
            withContext(Dispatchers.Default) {
                val threadId = threadDatabase.getOrCreateThreadIdFor(recipient)
                val mediaItems = mediaDatabase.getGalleryMediaForThread(threadId)
                    .use { cursor ->
                        cursor.asSequence()
                            .map { MediaRecord.from(application, it) }
                            .groupRecordsByTimeBuckets()
                    }

                val documentItems = mediaDatabase.getDocumentMediaForThread(threadId)
                    .use { cursor ->
                        cursor.asSequence()
                            .map { MediaRecord.from(application, it) }
                            .groupRecordsByRelativeTime()
                    }

                MediaOverviewContent(
                    mediaContent = mediaItems,
                    documentContent = documentItems,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableSelectedItemIDs = MutableStateFlow(emptySet<Long>())
    val selectedItemIDs: StateFlow<Set<Long>> get() = mutableSelectedItemIDs

    val inSelectionMode: StateFlow<Boolean> = selectedItemIDs
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableSelectedItemIDs.value.isNotEmpty())

    val canLongPress: StateFlow<Boolean> = inSelectionMode
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val mutableEvents = MutableSharedFlow<MediaOverviewEvent>()
    val events get() = mutableEvents

    private val mutableSelectedTab = MutableStateFlow(MediaOverviewTab.Media)
    val selectedTab: StateFlow<MediaOverviewTab> get() = mutableSelectedTab

    private val mutableShowingActionProgress = MutableStateFlow<String?>(null)
    val showingActionProgress: StateFlow<String?> get() = mutableShowingActionProgress

    private val selectedMedia: Sequence<MediaOverviewItem>
        get() {
            val selected = selectedItemIDs.value
            return mediaListState.value
                ?.mediaContent
                ?.asSequence()
                .orEmpty()
                .flatMap { it.second.asSequence() }
                .filter { it.id in selected }
        }

    private fun Sequence<MediaRecord>.groupRecordsByTimeBuckets(): List<Pair<BucketTitle, List<MediaOverviewItem>>> {
        return this
            .groupBy { record ->
                val time =
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(record.date), ZoneId.of("UTC"))
                timeBuckets.getBucketText(application, time)
                    ?: time.toLocalDate().withDayOfMonth(1)
            }
            .map { (bucket, records) ->
                val bucketTitle = when (bucket) {
                    is String -> bucket
                    is LocalDate -> bucket.format(monthTimeBucketFormatter)
                    else -> error("Invalid bucket type: $bucket")
                }

                bucketTitle to records.map { record ->
                    MediaOverviewItem(
                        id = record.attachment.attachmentId.rowId,
                        slide = MediaUtil.getSlideForAttachment(application, record.attachment),
                        mediaRecord = record,
                        date = bucketTitle
                    )
                }
            }
    }

    private fun Sequence<MediaRecord>.groupRecordsByRelativeTime(): List<Pair<BucketTitle, List<MediaOverviewItem>>> {
        return this
            .groupBy { record ->
                DateUtils.getRelativeDate(application, Locale.getDefault(), record.date)
            }
            .map { (bucket, records) ->
                bucket to records.map { record ->
                    MediaOverviewItem(
                        id = record.attachment.attachmentId.rowId,
                        slide = MediaUtil.getSlideForAttachment(application, record.attachment),
                        mediaRecord = record,
                        date = bucket
                    )
                }
            }
    }


    fun onItemClicked(item: MediaOverviewItem) {
        if (inSelectionMode.value) {
            if (item.slide.hasDocument()) {
                // We don't support selecting documents in selection mode
                return
            }

            val newSet = mutableSelectedItemIDs.value.toMutableSet()
            if (item.id in newSet) {
                newSet.remove(item.id)
            } else {
                newSet.add(item.id)
            }

            mutableSelectedItemIDs.value = newSet
        } else if (!item.slide.hasDocument()) {
            val mediaRecord = item.mediaRecord

            // The item clicked is a media item, so we should open the media viewer
            val intent = Intent(application, MediaPreviewActivity::class.java)
            intent.putExtra(MediaPreviewActivity.DATE_EXTRA, mediaRecord.date)
            intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, mediaRecord.attachment.size)
            intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, address)
            intent.putExtra(MediaPreviewActivity.OUTGOING_EXTRA, mediaRecord.isOutgoing)
            intent.putExtra(MediaPreviewActivity.LEFT_IS_RECENT_EXTRA, true)

            intent.setDataAndType(
                mediaRecord.attachment.dataUri,
                mediaRecord.contentType
            )

            viewModelScope.launch {
                mutableEvents.emit(MediaOverviewEvent.NavigateToActivity(intent))
            }
        } else {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(
                PartAuthority.getAttachmentPublicUri(item.slide.uri),
                item.slide.contentType
            )

            viewModelScope.launch {
                mutableEvents.emit(MediaOverviewEvent.NavigateToActivity(intent))
            }
        }
    }

    fun onTabItemClicked(tab: MediaOverviewTab) {
        mutableSelectedTab.value = tab
    }

    fun onItemLongClicked(id: Long) {
        mutableSelectedItemIDs.value = setOf(id)
    }

    fun onSaveClicked() {
        if (!inSelectionMode.value) {
            // Not in selection mode, so we should not be able to save
            return
        }

        viewModelScope.launch {
            val selectedMedia = selectedMedia.toList()

            mutableShowingActionProgress.value = application.resources.getString(R.string.saving)

            val attachments = selectedMedia
                .asSequence()
                .mapNotNull {
                    val uri = it.mediaRecord.attachment.dataUri ?: return@mapNotNull null
                    SaveAttachmentTask.Attachment(
                        uri = uri,
                        contentType = it.mediaRecord.contentType,
                        date = it.mediaRecord.date,
                        fileName = it.mediaRecord.attachment.fileName,
                    )
                }

            var savedDirectory: String? = null
            var successCount = 0
            var errorCount = 0

            for (attachment in attachments) {
                val directory = withContext(Dispatchers.Default) {
                    kotlin.runCatching {
                        SaveAttachmentTask.saveAttachment(application, attachment)
                    }.getOrNull()
                }

                if (directory == null) {
                    errorCount += 1
                } else {
                    savedDirectory = directory
                    successCount += 1
                }
            }

            if (successCount > 0) {
                mutableEvents.emit(MediaOverviewEvent.ShowSaveAttachmentSuccess(
                    savedDirectory.orEmpty(),
                    successCount
                ))
            } else if (errorCount > 0) {
                mutableEvents.emit(MediaOverviewEvent.ShowSaveAttachmentError(errorCount))
            }

            // Send a notification of attachment saved if we are in a 1to1 chat and the
            // attachments saved are from the other party (a.k.a let other person know
            // that you saved their attachments, but don't need to let the whole world know as
            // in groups/communities)
            if (selectedMedia.any { !it.mediaRecord.isOutgoing } &&
                successCount > 0 &&
                !address.isGroupOrCommunity) {
                withContext(Dispatchers.Default) {
                    val timestamp = SnodeAPI.nowWithOffset
                    val kind = DataExtractionNotification.Kind.MediaSaved(timestamp)
                    val message = DataExtractionNotification(kind)
                    MessageSender.send(message, address)
                }
            }

            mutableShowingActionProgress.value = null
            mutableSelectedItemIDs.value = emptySet()
        }

    }

    fun onDeleteClicked() {
        if (!inSelectionMode.value) {
            // Not in selection mode, so we should not be able to delete
            return
        }

        viewModelScope.launch {
            mutableShowingActionProgress.value = application.getString(R.string.deleting)

            // Delete the selected media items, and retrieve the thread ID for the address if any
            val threadId = withContext(Dispatchers.Default) {
                for (media in selectedMedia) {
                    kotlin.runCatching {
                        AttachmentUtil.deleteAttachment(application, media.mediaRecord.attachment)
                    }
                }

                threadDatabase.getThreadIdIfExistsFor(address.serialize())
            }

            // Notify the content provider that the thread has been updated
            if (threadId >= 0) {
                application.contentResolver.notifyChange(DatabaseContentProviders.Conversation.getUriForThread(threadId), null)
            }

            mutableShowingActionProgress.value = null
            mutableSelectedItemIDs.value = emptySet()
        }
    }

    fun onSelectAllClicked() {
        if (!inSelectionMode.value) {
            // Not in selection mode, so we should not be able to select all
            return
        }

        val allItems = mediaListState.value?.let { content ->
            when (selectedTab.value) {
                MediaOverviewTab.Media -> content.mediaContent
                MediaOverviewTab.Documents -> content.documentContent
            }
        } ?: return

        mutableSelectedItemIDs.value = allItems
            .asSequence()
            .flatMap { it.second }
            .mapTo(hashSetOf()) { it.id }
    }

    fun onBackClicked() {
        if (inSelectionMode.value) {
            // Clear selection mode by clear selecting items
            mutableSelectedItemIDs.value = emptySet()
        } else {
            viewModelScope.launch {
                mutableEvents.emit(MediaOverviewEvent.Close)
            }
        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(address: Address): Factory
    }

    class Factory @AssistedInject constructor(
        @Assisted private val address: Address,
        private val application: Application,
        private val threadDatabase: ThreadDatabase,
        private val mediaDatabase: MediaDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MediaOverviewViewModel(
            address,
            application,
            threadDatabase,
            mediaDatabase
        ) as T
    }
}


enum class MediaOverviewTab {
    Media,
    Documents,
}

sealed interface MediaOverviewEvent {
    data object Close : MediaOverviewEvent
    data class ShowSaveAttachmentError(val errorCount: Int) : MediaOverviewEvent
    data class ShowSaveAttachmentSuccess(val directory: String, val successCount: Int) : MediaOverviewEvent
    data class NavigateToActivity(val intent: Intent) : MediaOverviewEvent
}

typealias BucketTitle = String
typealias TabContent = List<Pair<BucketTitle, List<MediaOverviewItem>>>

data class MediaOverviewContent(
    val mediaContent: TabContent,
    val documentContent: TabContent
)

data class MediaOverviewItem(
    val id: Long,
    val slide: Slide,
    val date: String,
    val mediaRecord: MediaRecord,
) {
    val showPlayOverlay: Boolean
        get() = slide.hasPlayOverlay()

    val thumbnailUri: Uri?
        get() = slide.thumbnailUri

    val hasPlaceholder: Boolean
        get() = slide.hasPlaceholder()

    val fileName: String?
        get() = slide.fileName.orNull()

    val fileSize: Long
        get() = slide.fileSize

    fun placeholder(context: Context): Int {
        return slide.getPlaceholderRes(context.theme)
    }
}

