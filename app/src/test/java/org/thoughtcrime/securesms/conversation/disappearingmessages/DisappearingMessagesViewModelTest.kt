package org.thoughtcrime.securesms.conversation.disappearingmessages

import android.app.Application
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.ExpiryRadioOption
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.OptionsCard
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.UiState
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.GetString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val THREAD_ID = 1L
private const val LOCAL_NUMBER = "05---local---address"
private val LOCAL_ADDRESS = Address.fromSerialized(LOCAL_NUMBER)
private const val GROUP_NUMBER = "${GroupUtil.OPEN_GROUP_PREFIX}4133"
private val GROUP_ADDRESS = Address.fromSerialized(GROUP_NUMBER)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DisappearingMessagesViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock lateinit var application: Application
    @Mock lateinit var textSecurePreferences: TextSecurePreferences
    @Mock lateinit var messageExpirationManager: SSKEnvironment.MessageExpirationManagerProtocol
    @Mock lateinit var disappearingMessages: DisappearingMessages
    @Mock lateinit var threadDb: ThreadDatabase
    @Mock lateinit var groupDb: GroupDatabase
    @Mock lateinit var storage: Storage
    @Mock lateinit var recipient: Recipient
    @Mock lateinit var groupRecord: GroupRecord

    @Test
    fun `note to self, off, new config`() = runTest {
        mock1on1(ExpiryMode.NONE, LOCAL_ADDRESS)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = LOCAL_ADDRESS,
                isNoteToSelf = true,
                expiryMode = ExpiryMode.NONE,
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.NONE,
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    typeOption(ExpiryMode.NONE, selected = true),
                    timeOption(ExpiryType.AFTER_SEND, 12.hours),
                    timeOption(ExpiryType.AFTER_SEND, 1.days),
                    timeOption(ExpiryType.AFTER_SEND, 7.days),
                    timeOption(ExpiryType.AFTER_SEND, 14.days)
                )
            )
        )
    }

    @Test
    fun `note to self, off, old config`() = runTest {
        mock1on1(ExpiryMode.NONE, LOCAL_ADDRESS)

        val viewModel = createViewModel(isNewConfigEnabled = false)

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = LOCAL_ADDRESS,
                isNoteToSelf = true,
                expiryMode = ExpiryMode.Legacy(0),
                isNewConfigEnabled = false,
                persistedMode = ExpiryMode.Legacy(0),
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    typeOption(ExpiryMode.NONE, selected = false),
                    timeOption(ExpiryType.LEGACY, 12.hours),
                    timeOption(ExpiryType.LEGACY, 1.days),
                    timeOption(ExpiryType.LEGACY, 7.days),
                    timeOption(ExpiryType.LEGACY, 14.days)
                )
            )
        )
    }

    @Test
    fun `group, off, admin, new config`() = runTest {
        mockGroup(ExpiryMode.NONE, isAdmin = true)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = true,
                isSelfAdmin = true,
                address = GROUP_ADDRESS,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.NONE,
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.NONE,
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    typeOption(ExpiryMode.NONE, selected = true),
                    timeOption(ExpiryType.AFTER_SEND, 12.hours),
                    timeOption(ExpiryType.AFTER_SEND, 1.days),
                    timeOption(ExpiryType.AFTER_SEND, 7.days),
                    timeOption(ExpiryType.AFTER_SEND, 14.days)
                ),
                showGroupFooter = true
            )
        )
    }

    @Test
    fun `group, off, not admin, new config`() = runTest {
        mockGroup(ExpiryMode.NONE, isAdmin = false)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = true,
                isSelfAdmin = false,
                address = GROUP_ADDRESS,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.NONE,
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.NONE,
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    typeOption(ExpiryMode.NONE, enabled = false, selected = true),
                    timeOption(ExpiryType.AFTER_SEND, 12.hours, enabled = false),
                    timeOption(ExpiryType.AFTER_SEND, 1.days, enabled = false),
                    timeOption(ExpiryType.AFTER_SEND, 7.days, enabled = false),
                    timeOption(ExpiryType.AFTER_SEND, 14.days, enabled = false)
                ),
                showGroupFooter = true,
                showSetButton = false,
            )
        )
    }

    @Test
    fun `1-1 conversation, off, new config`() = runTest {
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")
        mock1on1(ExpiryMode.NONE, someAddress)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.NONE,
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.NONE,
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_delete_type,
                    typeOption(ExpiryMode.NONE, selected = true),
                    typeOption(12.hours, ExpiryType.AFTER_READ),
                    typeOption(1.days, ExpiryType.AFTER_SEND)
                )
            )
        )
    }

    @Test
    fun `1-1 conversation, 12 hours after send, new config`() = runTest {
        val time = 12.hours
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")
        mock1on1AfterSend(time, someAddress)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.AfterSend(12.hours.inWholeSeconds),
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.AfterSend(12.hours.inWholeSeconds),
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_delete_type,
                    typeOption(ExpiryMode.NONE),
                    typeOption(time, ExpiryType.AFTER_READ),
                    typeOption(time, ExpiryType.AFTER_SEND, selected = true)
                ),
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    timeOption(ExpiryType.AFTER_SEND, 12.hours, selected = true),
                    timeOption(ExpiryType.AFTER_SEND, 1.days),
                    timeOption(ExpiryType.AFTER_SEND, 7.days),
                    timeOption(ExpiryType.AFTER_SEND, 14.days)
                )
            )
        )
    }

    @Test
    fun `1-1 conversation, 12 hours legacy, old config`() = runTest {
        val time = 12.hours
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")
        mock1on1(ExpiryType.LEGACY.mode(time), someAddress)

        val viewModel = createViewModel(isNewConfigEnabled = false)

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = ExpiryType.LEGACY.mode(12.hours),
                isNewConfigEnabled = false,
                persistedMode = ExpiryType.LEGACY.mode(12.hours),
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_delete_type,
                    typeOption(ExpiryMode.NONE),
                    typeOption(time, ExpiryType.LEGACY, selected = true),
                    typeOption(12.hours, ExpiryType.AFTER_READ, enabled = false),
                    typeOption(1.days, ExpiryType.AFTER_SEND, enabled = false)
                ),
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    timeOption(ExpiryType.LEGACY, 12.hours, selected = true),
                    timeOption(ExpiryType.LEGACY, 1.days),
                    timeOption(ExpiryType.LEGACY, 7.days),
                    timeOption(ExpiryType.LEGACY, 14.days)
                )
            )
        )
    }

    @Test
    fun `1-1 conversation, 1 day after send, new config`() = runTest {
        val time = 1.days
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")
        mock1on1AfterSend(time, someAddress)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.AfterSend(1.days.inWholeSeconds),
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.AfterSend(1.days.inWholeSeconds),
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_delete_type,
                    typeOption(ExpiryMode.NONE),
                    typeOption(12.hours, ExpiryType.AFTER_READ),
                    typeOption(time, ExpiryType.AFTER_SEND, selected = true)
                ),
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    timeOption(ExpiryType.AFTER_SEND, 12.hours),
                    timeOption(ExpiryType.AFTER_SEND, 1.days, selected = true),
                    timeOption(ExpiryType.AFTER_SEND, 7.days),
                    timeOption(ExpiryType.AFTER_SEND, 14.days)
                )
            )
        )
    }

    @Test
    fun `1-1 conversation, 1 day after read, new config`() = runTest {
        val time = 1.days
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")

        mock1on1AfterRead(time, someAddress)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.AfterRead(1.days.inWholeSeconds),
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.AfterRead(1.days.inWholeSeconds),
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_delete_type,
                    typeOption(ExpiryMode.NONE),
                    typeOption(1.days, ExpiryType.AFTER_READ, selected = true),
                    typeOption(time, ExpiryType.AFTER_SEND)
                ),
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    timeOption(ExpiryType.AFTER_READ, 5.minutes),
                    timeOption(ExpiryType.AFTER_READ, 1.hours),
                    timeOption(ExpiryType.AFTER_READ, 12.hours),
                    timeOption(ExpiryType.AFTER_READ, 1.days, selected = true),
                    timeOption(ExpiryType.AFTER_READ, 7.days),
                    timeOption(ExpiryType.AFTER_READ, 14.days)
                )
            )
        )
    }

    @Test
    fun `1-1 conversation, init 12 hours after read, then select after send, new config`() = runTest {
        val time = 12.hours
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")

        mock1on1AfterRead(time, someAddress)

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.setValue(afterSendMode(1.days))

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = afterSendMode(1.days),
                isNewConfigEnabled = true,
                persistedMode = afterReadMode(12.hours),
                showDebugOptions = false
            )
        )

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                OptionsCard(
                    R.string.activity_disappearing_messages_delete_type,
                    typeOption(ExpiryMode.NONE),
                    typeOption(12.hours, ExpiryType.AFTER_READ),
                    typeOption(1.days, ExpiryType.AFTER_SEND, selected = true)
                ),
                OptionsCard(
                    R.string.activity_disappearing_messages_timer,
                    timeOption(ExpiryType.AFTER_SEND, 12.hours),
                    timeOption(ExpiryType.AFTER_SEND, 1.days, selected = true),
                    timeOption(ExpiryType.AFTER_SEND, 7.days),
                    timeOption(ExpiryType.AFTER_SEND, 14.days)
                )
            )
        )
    }

    private fun timeOption(
        type: ExpiryType,
        time: Duration,
        enabled: Boolean = true,
        selected: Boolean = false
    ) = ExpiryRadioOption(
        value = type.mode(time),
        title = GetString(time),
        enabled = enabled,
        selected = selected
    )

    private fun afterSendMode(time: Duration) = ExpiryMode.AfterSend(time.inWholeSeconds)
    private fun afterReadMode(time: Duration) = ExpiryMode.AfterRead(time.inWholeSeconds)

    private fun mock1on1AfterRead(time: Duration, someAddress: Address) {
        mock1on1(ExpiryType.AFTER_READ.mode(time), someAddress)
    }

    private fun mock1on1AfterSend(time: Duration, someAddress: Address) {
        mock1on1(ExpiryType.AFTER_SEND.mode(time), someAddress)
    }

    private fun mock1on1(mode: ExpiryMode, someAddress: Address) {
        mockStuff(mode)

        whenever(recipient.address).thenReturn(someAddress)
    }

    private fun mockGroup(mode: ExpiryMode, isAdmin: Boolean) {
        mockStuff(mode)

        whenever(recipient.address).thenReturn(GROUP_ADDRESS)
        whenever(recipient.isClosedGroupRecipient).thenReturn(true)
        whenever(groupDb.getGroup(any<String>())).thenReturn(Optional.of(groupRecord))
        whenever(groupRecord.admins).thenReturn(
            buildList {
                if (isAdmin) add(LOCAL_ADDRESS)
            }
        )
    }

    private fun mockStuff(mode: ExpiryMode) {
        val config = config(mode)
        whenever(threadDb.getRecipientForThreadId(Mockito.anyLong())).thenReturn(recipient)
        whenever(storage.getExpirationConfiguration(Mockito.anyLong())).thenReturn(config)
        whenever(textSecurePreferences.getLocalNumber()).thenReturn(LOCAL_NUMBER)
    }

    private fun config(mode: ExpiryMode) = ExpirationConfiguration(
        threadId = THREAD_ID,
        expiryMode = mode,
        updatedTimestampMs = 0
    )

    private fun createViewModel(isNewConfigEnabled: Boolean = true) = DisappearingMessagesViewModel(
        THREAD_ID,
        application,
        textSecurePreferences,
        messageExpirationManager,
        disappearingMessages,
        threadDb,
        groupDb,
        storage,
        isNewConfigEnabled,
        showDebugOptions = false
    )
}

fun typeOption(time: Duration, type: ExpiryType, selected: Boolean = false, enabled: Boolean = true) =
    typeOption(type.mode(time), selected, enabled)

fun typeOption(mode: ExpiryMode, selected: Boolean = false, enabled: Boolean = true) =
    ExpiryRadioOption(
        mode,
        GetString(mode.type.title),
        mode.type.subtitle?.let(::GetString),
        GetString(mode.type.contentDescription),
        selected = selected,
        enabled = enabled
    )
