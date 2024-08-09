package org.thoughtcrime.securesms.media

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.asSequence
import org.thoughtcrime.securesms.util.observeChanges
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

class MediaOverviewViewModel(
    address: Address,
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
                            .groupRecordsByDate()
                    }

                val documentItems = mediaDatabase.getDocumentMediaForThread(threadId)
                    .use { cursor ->
                        cursor.asSequence()
                            .map { MediaRecord.from(application, it) }
                            .groupRecordsByDate()
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

    private val mutableInSelectionMode = MutableStateFlow(false)
    val inSelectionMode: StateFlow<Boolean> get() = mutableInSelectionMode

    val canLongPress: StateFlow<Boolean> = inSelectionMode
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val mutableEvents = MutableSharedFlow<MediaOverviewEvent>()
    val events get() = mutableEvents

    private val mutableSelectedTab = MutableStateFlow(MediaOverviewTab.Media)
    val selectedTab: StateFlow<MediaOverviewTab> get() = mutableSelectedTab

    private fun Sequence<MediaRecord>.groupRecordsByDate(): List<Pair<BucketTitle, List<MediaOverviewItem>>> {
        return this
            .groupBy { record ->
                val time =
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(record.date), ZoneId.of("UTC"))
                timeBuckets.getBucketText(time)?.let(application::getString)
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
                        slide = MediaUtil.getSlideForAttachment(application, record.attachment)
                    )
                }
            }
    }

    fun onItemClicked(id: Long) {
        if (inSelectionMode.value) {
            val newSet = mutableSelectedItemIDs.value.toMutableSet()
            if (id in newSet) {
                newSet.remove(id)
            } else {
                newSet.add(id)
            }

            mutableSelectedItemIDs.value = newSet
            if (newSet.isEmpty()) {
                mutableInSelectionMode.value = false
            }
        } else {
            mutableEvents.tryEmit(MediaOverviewEvent.NavigateToMediaDetail(id))
        }
    }

    fun onTabItemClicked(tab: MediaOverviewTab) {
        if (inSelectionMode.value) {
            // Not allowing to switch tabs while in selection mode
            return
        }

        mutableSelectedTab.value = tab
    }

    fun onItemLongClicked(id: Long) {
        mutableInSelectionMode.value = true
        mutableSelectedItemIDs.value = emptySet()
        onItemClicked(id)
    }

    fun onSaveClicked() {
        TODO("Not yet implemented")
    }

    fun onDeleteClicked() {
        TODO("Not yet implemented")
    }

    fun onSelectAllClicked() {
        if (!inSelectionMode.value) return

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
            mutableInSelectionMode.value = false
            mutableSelectedItemIDs.value = emptySet()
        } else {
            mutableEvents.tryEmit(MediaOverviewEvent.Close)
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


class FixedTimeBuckets(
    private val startOfToday: ZonedDateTime,
    private val startOfYesterday: ZonedDateTime,
    private val startOfThisWeek: ZonedDateTime,
    private val startOfThisMonth: ZonedDateTime
) {
    constructor(now: ZonedDateTime = ZonedDateTime.now()) : this(
        startOfToday = now.toLocalDate().atStartOfDay(now.zone),
        startOfYesterday = now.toLocalDate().minusDays(1).atStartOfDay(now.zone),
        startOfThisWeek = now.toLocalDate()
            .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
            .atStartOfDay(now.zone),
        startOfThisMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
    )

    @StringRes
    fun getBucketText(time: ZonedDateTime): Int? {
        return when {
            time >= startOfToday -> R.string.BucketedThreadMedia_Today
            time >= startOfYesterday -> R.string.BucketedThreadMedia_Yesterday
            time >= startOfThisWeek -> R.string.BucketedThreadMedia_This_week
            time >= startOfThisMonth -> R.string.BucketedThreadMedia_This_month
            else -> null
        }
    }
}

enum class MediaOverviewTab {
    Media,
    Documents,
}

sealed interface MediaOverviewEvent {
    data object Close : MediaOverviewEvent
    data class NavigateToMediaDetail(val id: Long) : MediaOverviewEvent
}

typealias BucketTitle = String
typealias TabContent = List<Pair<BucketTitle, List<MediaOverviewItem>>>

data class MediaOverviewContent(
    val mediaContent: TabContent,
    val documentContent: TabContent
)

data class MediaOverviewItem(
    val id: Long,
    val slide: Slide
)

