package org.thoughtcrime.securesms.conversation.v2

import android.Manifest
import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Pair
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.annimon.stream.Stream
import com.bumptech.glide.Glide
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityConversationV2Binding
import network.loki.messenger.libsession_util.util.ExpiryMode
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.applyExpiryMode
import org.session.libsession.messaging.messages.control.DataExtractionNotification
import org.session.libsession.messaging.messages.signal.OutgoingMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.Reaction
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.utilities.AccountId
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.MediaTypes
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.CONVERSATION_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.Stub
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.concurrent.SimpleTask
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.RecipientModifiedListener
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.ListenableFuture
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.attachments.ScreenshotObserver
import org.thoughtcrime.securesms.audio.AudioRecorder
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.contacts.SelectContactsActivity.Companion.selectedContactsKey
import org.thoughtcrime.securesms.conversation.ConversationActionBarDelegate
import org.thoughtcrime.securesms.conversation.disappearingmessages.DisappearingMessagesActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationReactionOverlay.OnActionSelectedListener
import org.thoughtcrime.securesms.conversation.v2.ConversationReactionOverlay.OnReactionSelectedListener
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.*
import org.thoughtcrime.securesms.conversation.v2.MessageDetailActivity.Companion.MESSAGE_TIMESTAMP
import org.thoughtcrime.securesms.conversation.v2.MessageDetailActivity.Companion.ON_COPY
import org.thoughtcrime.securesms.conversation.v2.MessageDetailActivity.Companion.ON_DELETE
import org.thoughtcrime.securesms.conversation.v2.MessageDetailActivity.Companion.ON_REPLY
import org.thoughtcrime.securesms.conversation.v2.MessageDetailActivity.Companion.ON_RESEND
import org.thoughtcrime.securesms.conversation.v2.MessageDetailActivity.Companion.ON_SAVE
import org.thoughtcrime.securesms.conversation.v2.dialogs.BlockedDialog
import org.thoughtcrime.securesms.conversation.v2.dialogs.LinkPreviewDialog
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarButton
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarRecordingViewDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.VoiceRecorderConstants.ANIMATE_LOCK_DURATION_MS
import org.thoughtcrime.securesms.conversation.v2.input_bar.VoiceRecorderConstants.SHOW_HIDE_VOICE_UI_DURATION_MS
import org.thoughtcrime.securesms.conversation.v2.input_bar.VoiceRecorderState
import org.thoughtcrime.securesms.conversation.v2.input_bar.mentions.MentionCandidateAdapter
import org.thoughtcrime.securesms.conversation.v2.mention.MentionViewModel
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallback
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallbackDelegate
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuHelper
import org.thoughtcrime.securesms.conversation.v2.messages.ControlMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageViewDelegate
import org.thoughtcrime.securesms.conversation.v2.search.SearchBottomBar
import org.thoughtcrime.securesms.conversation.v2.search.SearchViewModel
import org.thoughtcrime.securesms.conversation.v2.utilities.AttachmentManager
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.ResendMessageUtilities
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.ReactionDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.giph.ui.GiphyActivity
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.home.HomeActivity
import org.thoughtcrime.securesms.home.startHomeActivity
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel.LinkPreviewState
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivity
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.GifSlide
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.reactions.ReactionsDialogFragment
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiDialogFragment
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.util.ActivityDispatcher
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.NetworkUtils
import org.thoughtcrime.securesms.util.SaveAttachmentTask
import org.thoughtcrime.securesms.util.drawToBitmap
import org.thoughtcrime.securesms.util.isScrolledToBottom
import org.thoughtcrime.securesms.util.isScrolledToWithin30dpOfBottom
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.show
import org.thoughtcrime.securesms.util.toPx
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes


private const val TAG = "ConversationActivityV2"

// Some things that seemingly belong to the input bar (e.g. the voice message recording UI) are actually
// part of the conversation activity layout. This is just because it makes the layout a lot simpler. The
// price we pay is a bit of back and forth between the input bar and the conversation activity.
@AndroidEntryPoint
class ConversationActivityV2 : PassphraseRequiredActionBarActivity(), InputBarDelegate,
    InputBarRecordingViewDelegate, AttachmentManager.AttachmentListener, ActivityDispatcher,
    ConversationActionModeCallbackDelegate, VisibleMessageViewDelegate, RecipientModifiedListener,
    SearchBottomBar.EventListener, LoaderManager.LoaderCallbacks<Cursor>, ConversationActionBarDelegate,
    OnReactionSelectedListener, ReactWithAnyEmojiDialogFragment.Callback, ReactionsDialogFragment.Callback,
    ConversationMenuHelper.ConversationMenuListener {

    private lateinit var binding: ActivityConversationV2Binding

    @Inject lateinit var textSecurePreferences: TextSecurePreferences
    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var mmsSmsDb: MmsSmsDatabase
    @Inject lateinit var lokiThreadDb: LokiThreadDatabase
    @Inject lateinit var sessionContactDb: SessionContactDatabase
    @Inject lateinit var groupDb: GroupDatabase
    @Inject lateinit var smsDb: SmsDatabase
    @Inject lateinit var mmsDb: MmsDatabase
    @Inject lateinit var lokiMessageDb: LokiMessageDatabase
    @Inject lateinit var storage: Storage
    @Inject lateinit var reactionDb: ReactionDatabase
    @Inject lateinit var viewModelFactory: ConversationViewModel.AssistedFactory
    @Inject lateinit var mentionViewModelFactory: MentionViewModel.AssistedFactory

    private val screenshotObserver by lazy {
        ScreenshotObserver(this, Handler(Looper.getMainLooper())) {
            // post screenshot message
            sendScreenshotNotification()
        }
    }

    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val linkPreviewViewModel: LinkPreviewViewModel by lazy {
        ViewModelProvider(this, LinkPreviewViewModel.Factory(LinkPreviewRepository()))
            .get(LinkPreviewViewModel::class.java)
    }

    private val threadId: Long by lazy {
        var threadId = intent.getLongExtra(THREAD_ID, -1L)
        if (threadId == -1L) {
            intent.getParcelableExtra<Address>(ADDRESS)?.let { it ->
                threadId = threadDb.getThreadIdIfExistsFor(it.serialize())
                if (threadId == -1L) {
                    val accountId = AccountId(it.serialize())
                    val openGroup = lokiThreadDb.getOpenGroupChat(intent.getLongExtra(FROM_GROUP_THREAD_ID, -1))
                    val address = if (accountId.prefix == IdPrefix.BLINDED && openGroup != null) {
                        storage.getOrCreateBlindedIdMapping(accountId.hexString, openGroup.server, openGroup.publicKey).accountId?.let {
                            fromSerialized(it)
                        } ?: GroupUtil.getEncodedOpenGroupInboxID(openGroup, accountId)
                    } else {
                        it
                    }
                    val recipient = Recipient.from(this, address, false)
                    threadId = storage.getOrCreateThreadIdFor(recipient.address)
                }
            } ?: finish()
        }

        threadId
    }

    private val viewModel: ConversationViewModel by viewModels {
        viewModelFactory.create(threadId, MessagingModuleConfiguration.shared.getUserED25519KeyPair())
    }
    private var actionMode: ActionMode? = null
    private var unreadCount = Int.MAX_VALUE
    // Attachments
    private val audioRecorder = AudioRecorder(this)
    private val stopAudioHandler = Handler(Looper.getMainLooper())
    private val stopVoiceMessageRecordingTask = Runnable { sendVoiceMessage() }
    private val attachmentManager by lazy { AttachmentManager(this, this) }
    private var isLockViewExpanded = false
    private var isShowingAttachmentOptions = false
    // Mentions
    private val mentionViewModel: MentionViewModel by viewModels {
        mentionViewModelFactory.create(threadId)
    }
    private val mentionCandidateAdapter = MentionCandidateAdapter {
        mentionViewModel.onCandidateSelected(it.member.publicKey)
    }
    // Search
    val searchViewModel: SearchViewModel by viewModels()
    var searchViewItem: MenuItem? = null

    private val bufferedLastSeenChannel = Channel<Long>(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var emojiPickerVisible = false

    // Queue of timestamps used to rate-limit emoji reactions
    private val emojiRateLimiterQueue = LinkedList<Long>()

    // Constants used to enforce the given maximum emoji reactions allowed per minute (emoji reactions
    // that occur above this limit will result in a "Slow down" toast rather than adding the reaction).
    private val EMOJI_REACTIONS_ALLOWED_PER_MINUTE = 20
    private val ONE_MINUTE_IN_MILLISECONDS = 1.minutes.inWholeMilliseconds

    private val isScrolledToBottom: Boolean
        get() = binding.conversationRecyclerView.isScrolledToBottom

    private val isScrolledToWithin30dpOfBottom: Boolean
        get() = binding.conversationRecyclerView.isScrolledToWithin30dpOfBottom

    private val layoutManager: LinearLayoutManager?
        get() { return binding.conversationRecyclerView.layoutManager as LinearLayoutManager? }

    private val seed by lazy {
        var hexEncodedSeed = IdentityKeyUtil.retrieve(this, IdentityKeyUtil.LOKI_SEED)
        if (hexEncodedSeed == null) {
            hexEncodedSeed = IdentityKeyUtil.getIdentityKeyPair(this).hexEncodedPrivateKey // Legacy account
        }

        val appContext = applicationContext
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(appContext, fileName)
        }
        MnemonicCodec(loadFileContents).encode(hexEncodedSeed, MnemonicCodec.Language.Configuration.english)
    }

    // There is a bug when initially joining a community where all messages will immediately be marked
    // as read if we reverse the message list so this is now hard-coded to false
    private val reverseMessageList = false

    private val adapter by lazy {
        val cursor = mmsSmsDb.getConversation(viewModel.threadId, reverseMessageList)
        val adapter = ConversationAdapter(
            this,
            cursor,
            storage.getLastSeen(viewModel.threadId),
            reverseMessageList,
            onItemPress = { message, position, view, event ->
                handlePress(message, position, view, event)
            },
            onItemSwipeToReply = { message, _ ->
                handleSwipeToReply(message)
            },
            onItemLongPress = { message, position, view ->
                if (!viewModel.isMessageRequestThread &&
                    viewModel.canReactToMessages
                ) {
                    showConversationReaction(message, view)
                } else {
                    selectMessage(message, position)
                }
            },
            onDeselect = { message, position ->
                actionMode?.let {
                    onDeselect(message, position, it)
                }
            },
            onAttachmentNeedsDownload = viewModel::onAttachmentDownloadRequest,
            glide = glide,
            lifecycleCoroutineScope = lifecycleScope
        )
        adapter.visibleMessageViewDelegate = this

        // Register an AdapterDataObserver to scroll us to the bottom of the RecyclerView for if
        // we're already near the the bottom and the data changes.
        adapter.registerAdapterDataObserver(ConversationAdapterDataObserver(binding.conversationRecyclerView, adapter))

        adapter
    }

    private val glide by lazy { Glide.with(this) }
    private val lockViewHitMargin by lazy { toPx(40, resources) }
    private val gifButton by lazy { InputBarButton(this, R.drawable.ic_gif_white_24dp, hasOpaqueBackground = true, isGIFButton = true) }
    private val documentButton by lazy { InputBarButton(this, R.drawable.ic_document_small_dark, hasOpaqueBackground = true) }
    private val libraryButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_library_24, hasOpaqueBackground = true) }
    private val cameraButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_camera_24, hasOpaqueBackground = true) }
    private val messageToScrollTimestamp = AtomicLong(-1)
    private val messageToScrollAuthor = AtomicReference<Address?>(null)
    private val firstLoad = AtomicBoolean(true)

    private lateinit var reactionDelegate: ConversationReactionDelegate
    private val reactWithAnyEmojiStartPage = -1

    // Properties for what message indices are visible previously & now, as well as the scroll state
    private var previousLastVisibleRecyclerViewIndex: Int = RecyclerView.NO_POSITION
    private var currentLastVisibleRecyclerViewIndex:  Int = RecyclerView.NO_POSITION
    private var recyclerScrollState: Int = RecyclerView.SCROLL_STATE_IDLE

    // Lower limit for the length of voice messages - any lower and we inform the user rather than sending
    private val MINIMUM_VOICE_MESSAGE_DURATION_MS = 1000L

    // region Settings
    companion object {
        // Extras
        const val THREAD_ID = "thread_id"
        const val ADDRESS = "address"
        const val FROM_GROUP_THREAD_ID = "from_group_thread_id"
        const val SCROLL_MESSAGE_ID = "scroll_message_id"
        const val SCROLL_MESSAGE_AUTHOR = "scroll_message_author"
        // Request codes
        const val PICK_DOCUMENT = 2
        const val TAKE_PHOTO = 7
        const val PICK_GIF = 10
        const val PICK_FROM_LIBRARY = 12
        const val INVITE_CONTACTS = 124
    }
    // endregion

    fun showOpenUrlDialog(url: String){
        viewModel.onCommand(ShowOpenUrlDialog(url))
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivityConversationV2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // set the compose dialog content
        binding.dialogOpenUrl.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val dialogsState by viewModel.dialogsState.collectAsState()
                ConversationV2Dialogs(
                    dialogsState = dialogsState,
                    sendCommand = viewModel::onCommand
                )
            }
        }

        // messageIdToScroll
        messageToScrollTimestamp.set(intent.getLongExtra(SCROLL_MESSAGE_ID, -1))
        messageToScrollAuthor.set(intent.getParcelableExtra(SCROLL_MESSAGE_AUTHOR))
        val recipient = viewModel.recipient
        val openGroup = recipient.let { viewModel.openGroup }
        if (recipient == null || (recipient.isCommunityRecipient && openGroup == null)) {
            Toast.makeText(this, getString(R.string.conversationsDeleted), Toast.LENGTH_LONG).show()
            return finish()
        }

        setUpToolBar()
        setUpInputBar()
        setUpLinkPreviewObserver()
        restoreDraftIfNeeded()
        setUpUiStateObserver()

        binding.scrollToBottomButton.setOnClickListener {
            val layoutManager = binding.conversationRecyclerView.layoutManager as LinearLayoutManager
            val targetPosition = if (reverseMessageList) 0 else adapter.itemCount

            if (layoutManager.isSmoothScrolling) {
                binding.conversationRecyclerView.scrollToPosition(targetPosition)
            } else {
                // It looks like 'smoothScrollToPosition' will actually load all intermediate items in
                // order to do the scroll, this can be very slow if there are a lot of messages so
                // instead we check the current position and if there are more than 10 items to scroll
                // we jump instantly to the 10th item and scroll from there (this should happen quick
                // enough to give a similar scroll effect without having to load everything)
//                val position = if (reverseMessageList) layoutManager.findFirstVisibleItemPosition() else layoutManager.findLastVisibleItemPosition()
//                val targetBuffer = if (reverseMessageList) 10 else Math.max(0, (adapter.itemCount - 1) - 10)
//                if (position > targetBuffer) {
//                    binding.conversationRecyclerView?.scrollToPosition(targetBuffer)
//                }

                binding.conversationRecyclerView.post {
                    binding.conversationRecyclerView.smoothScrollToPosition(targetPosition)
                }
            }
        }

        updateUnreadCountIndicator()
        updatePlaceholder()
        setUpBlockedBanner()
        binding.searchBottomBar.setEventListener(this)
        updateSendAfterApprovalText()
        setUpMessageRequestsBar()

        // Note: Do not `showOrHideInputIfNeeded` here - we'll never start this activity w/ the
        // keyboard visible and have no need to immediately display it.

        val weakActivity = WeakReference(this)

        lifecycleScope.launch(Dispatchers.IO) {
            // Note: We are accessing the `adapter` property because we want it to be loaded on
            // the background thread to avoid blocking the UI thread and potentially hanging when
            // transitioning to the activity
            weakActivity.get()?.adapter ?: return@launch

            // 'Get' instead of 'GetAndSet' here because we want to trigger the highlight in 'onFirstLoad'
            // by triggering 'jumpToMessage' using these values
            val messageTimestamp = messageToScrollTimestamp.get()
            val author = messageToScrollAuthor.get()
            val targetPosition = if (author != null && messageTimestamp >= 0) mmsSmsDb.getMessagePositionInConversation(viewModel.threadId, messageTimestamp, author, reverseMessageList) else -1

            withContext(Dispatchers.Main) {
                setUpRecyclerView()
                setUpTypingObserver()
                setUpRecipientObserver()
                getLatestOpenGroupInfoIfNeeded()
                setUpSearchResultObserver()
                scrollToFirstUnreadMessageIfNeeded()
                setUpOutdatedClientBanner()

                if (author != null && messageTimestamp >= 0 && targetPosition >= 0) {
                    binding.conversationRecyclerView.scrollToPosition(targetPosition)
                }
                else {
                    scrollToFirstUnreadMessageIfNeeded(true)
                }
            }
        }

        val reactionOverlayStub: Stub<ConversationReactionOverlay> =
            ViewUtil.findStubById(this, R.id.conversation_reaction_scrubber_stub)
        reactionDelegate = ConversationReactionDelegate(reactionOverlayStub)
        reactionDelegate.setOnReactionSelectedListener(this)
        lifecycleScope.launch {
                // only update the conversation every 3 seconds maximum
                // channel is rendezvous and shouldn't block on try send calls as often as we want
                bufferedLastSeenChannel.receiveAsFlow()
                    .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
                    .collectLatest {
                        withContext(Dispatchers.IO) {
                            try {
                                if (it > storage.getLastSeen(viewModel.threadId)) {
                                    storage.markConversationAsRead(viewModel.threadId, it)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "bufferedLastSeenChannel collectLatest", e)
                            }
                        }
                    }
        }

        setupMentionView()
    }

    private fun setupMentionView() {
        binding?.conversationMentionCandidates?.let { view ->
            view.adapter = mentionCandidateAdapter
            view.itemAnimator = null
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mentionViewModel.autoCompleteState
                    .collectLatest { state ->
                        mentionCandidateAdapter.candidates =
                            (state as? MentionViewModel.AutoCompleteState.Result)?.members.orEmpty()
                    }
            }
        }

        binding?.inputBar?.setInputBarEditableFactory(mentionViewModel.editableFactory)
    }

    override fun onResume() {
        super.onResume()
        ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(viewModel.threadId)

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver
        )
        viewModel.run {
            binding.toolbarContent?.update(recipient ?: return, openGroup, expirationConfiguration)
        }
    }

    override fun onPause() {
        super.onPause()
        ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(-1)
        contentResolver.unregisterContentObserver(screenshotObserver)
    }

    override fun getSystemService(name: String): Any? {
        if (name == ActivityDispatcher.SERVICE) {
            return this
        }
        return super.getSystemService(name)
    }

    override fun dispatchIntent(body: (Context) -> Intent?) {
        body(this)?.let { push(it, false) }
    }

    override fun showDialog(dialogFragment: DialogFragment, tag: String?) {
        dialogFragment.show(supportFragmentManager, tag)
    }

    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
        return ConversationLoader(viewModel.threadId, reverseMessageList, this@ConversationActivityV2)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        val oldCount = adapter.itemCount
        val newCount = cursor?.count ?: 0
        adapter.changeCursor(cursor)

        if (cursor != null) {
            val messageTimestamp = messageToScrollTimestamp.getAndSet(-1)
            val author = messageToScrollAuthor.getAndSet(null)
            val initialUnreadCount = mmsSmsDb.getUnreadCount(viewModel.threadId)

            // Update the unreadCount value to be loaded from the database since we got a new message
            if (firstLoad.get() || oldCount != newCount || initialUnreadCount != unreadCount) {
                // Update the unreadCount value to be loaded from the database since we got a new
                // message (we need to store it in a local variable as it can get overwritten on
                // another thread before the 'firstLoad.getAndSet(false)' case below)
                unreadCount = initialUnreadCount
                updateUnreadCountIndicator()
            }

            if (author != null && messageTimestamp >= 0) {
                jumpToMessage(author, messageTimestamp, firstLoad.get(), null)
            } else {
                if (firstLoad.getAndSet(false)) scrollToFirstUnreadMessageIfNeeded(true)
                handleRecyclerViewScrolled()
            }
        }
        updatePlaceholder()
        viewModel.recipient?.let {
            maybeUpdateToolbar(recipient = it)
            setUpOutdatedClientBanner()
        }
    }

    override fun onLoaderReset(cursor: Loader<Cursor>) {
        adapter.changeCursor(null)
    }

    // called from onCreate
    private fun setUpRecyclerView() {
        binding.conversationRecyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, reverseMessageList)
        binding.conversationRecyclerView.layoutManager = layoutManager
        // Workaround for the fact that CursorRecyclerViewAdapter doesn't auto-update automatically (even though it says it will)
        LoaderManager.getInstance(this).restartLoader(0, null, this)
        binding.conversationRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // The unreadCount check is to prevent us scrolling to the bottom when we first enter a conversation
                if (recyclerScrollState == RecyclerView.SCROLL_STATE_IDLE && unreadCount != Int.MAX_VALUE) {
                    scrollToMostRecentMessageIfWeShould()
                }
                handleRecyclerViewScrolled()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                recyclerScrollState = newState
            }
        })

        lifecycleScope.launch {
            viewModel.isAdmin.collect{
                adapter.isAdmin = it
            }
        }
    }

    private fun scrollToMostRecentMessageIfWeShould() {
        // Grab an initial 'previous' last visible message..
        if (previousLastVisibleRecyclerViewIndex == RecyclerView.NO_POSITION) {
            previousLastVisibleRecyclerViewIndex = layoutManager?.findLastVisibleItemPosition()!!
        }

        // ..and grab the 'current' last visible message.
        currentLastVisibleRecyclerViewIndex = layoutManager?.findLastVisibleItemPosition()!!

        // If the current last visible message index is less than the previous one (i.e. we've
        // lost visibility of one or more messages due to showing the IME keyboard) AND we're
        // at the bottom of the message feed..
        val atBottomAndTrueLastNoLongerVisible = currentLastVisibleRecyclerViewIndex <= previousLastVisibleRecyclerViewIndex &&
                                                 !binding.scrollToBottomButton.isVisible

        // ..OR we're at the last message or have received a new message..
        val atLastOrReceivedNewMessage = currentLastVisibleRecyclerViewIndex == (adapter.itemCount - 1)

        // ..then scroll the recycler view to the last message on resize. Note: We cannot just call
        // scroll/smoothScroll - we have to `post` it or nothing happens!
        if (atBottomAndTrueLastNoLongerVisible || atLastOrReceivedNewMessage) {
            binding.conversationRecyclerView.post {
                binding.conversationRecyclerView.smoothScrollToPosition(adapter.itemCount)
            }
        }

        // Update our previous last visible view index to the current one
        previousLastVisibleRecyclerViewIndex = currentLastVisibleRecyclerViewIndex
    }

    // called from onCreate
    private fun setUpToolBar() {
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar ?: return
        val recipient = viewModel.recipient ?: return
        actionBar.title = ""
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeButtonEnabled(true)
        binding.toolbarContent.bind(
            this,
            viewModel.threadId,
            recipient,
            viewModel.expirationConfiguration,
            viewModel.openGroup
        )
        maybeUpdateToolbar(recipient)
    }

    // called from onCreate
    private fun setUpInputBar() {
        binding.inputBar.delegate = this
        binding.inputBarRecordingView.delegate = this
        // GIF button
        binding.gifButtonContainer.addView(gifButton, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        gifButton.onUp = { showGIFPicker() }
        gifButton.snIsEnabled = false
        // Document button
        binding.documentButtonContainer.addView(documentButton, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        documentButton.onUp = { showDocumentPicker() }
        documentButton.snIsEnabled = false
        // Library button
        binding.libraryButtonContainer.addView(libraryButton, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        libraryButton.onUp = { pickFromLibrary() }
        libraryButton.snIsEnabled = false
        // Camera button
        binding.cameraButtonContainer.addView(cameraButton, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        cameraButton.onUp = { showCamera() }
        cameraButton.snIsEnabled = false
    }

    // called from onCreate
    private fun restoreDraftIfNeeded() {
        val mediaURI = intent.data
        val mediaType = AttachmentManager.MediaType.from(intent.type)
        val mimeType =  MediaUtil.getMimeType(this, mediaURI)
        if (mediaURI != null && mediaType != null) {
            if (mimeType != null && (AttachmentManager.MediaType.IMAGE == mediaType || AttachmentManager.MediaType.GIF == mediaType || AttachmentManager.MediaType.VIDEO == mediaType)) {
                val media = Media(mediaURI, mimeType, 0, 0, 0, 0, Optional.absent(), Optional.absent())
                startActivityForResult(MediaSendActivity.buildEditorIntent(this, listOf( media ), viewModel.recipient!!, ""), PICK_FROM_LIBRARY)
                return
            } else {
                prepMediaForSending(mediaURI, mediaType).addListener(object : ListenableFuture.Listener<Boolean> {

                    override fun onSuccess(result: Boolean?) {
                        sendAttachments(attachmentManager.buildSlideDeck().asAttachments(), null)
                    }

                    override fun onFailure(e: ExecutionException?) {
                        Toast.makeText(this@ConversationActivityV2, R.string.attachmentsErrorLoad, Toast.LENGTH_LONG).show()
                    }
                })
                return
            }
        } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            val dataTextExtra = intent.getCharSequenceExtra(Intent.EXTRA_TEXT) ?: ""
            binding.inputBar.text = dataTextExtra.toString()
        } else {
            viewModel.getDraft()?.let { text ->
                binding.inputBar.text = text
            }
        }
    }

    // called from onCreate
    private fun setUpTypingObserver() {
        ApplicationContext.getInstance(this).typingStatusRepository.getTypists(viewModel.threadId).observe(this) { state ->
            val recipients = if (state != null) state.typists else listOf()
            // FIXME: Also checking isScrolledToBottom is a quick fix for an issue where the
            //        typing indicator overlays the recycler view when scrolled up
            val viewContainer = binding.typingIndicatorViewContainer
            viewContainer.isVisible = recipients.isNotEmpty() && isScrolledToBottom
            viewContainer.setTypists(recipients)
        }
        if (textSecurePreferences.isTypingIndicatorsEnabled()) {
            binding.inputBar.addTextChangedListener {
                ApplicationContext.getInstance(this).typingStatusSender.onTypingStarted(viewModel.threadId)
            }
        }
    }

    private fun setUpRecipientObserver() {
        viewModel.recipient?.addListener(this)
    }

    private fun tearDownRecipientObserver() {
        viewModel.recipient?.removeListener(this)
    }

    private fun getLatestOpenGroupInfoIfNeeded() {
        val openGroup = viewModel.openGroup ?: return
        OpenGroupApi.getMemberCount(openGroup.room, openGroup.server) successUi {
            binding.toolbarContent.updateSubtitle(viewModel.recipient!!, openGroup, viewModel.expirationConfiguration)
            maybeUpdateToolbar(viewModel.recipient!!)
        }
    }

    // called from onCreate
    private fun setUpBlockedBanner() {
        val recipient = viewModel.recipient?.takeUnless { it.isGroupRecipient } ?: return
        binding.blockedBannerTextView.text = applicationContext.getString(R.string.blockBlockedDescription)
        binding.blockedBanner.isVisible = recipient.isBlocked
        binding.blockedBanner.setOnClickListener { viewModel.unblock() }
    }

    private fun setUpOutdatedClientBanner() {
        val legacyRecipient = viewModel.legacyBannerRecipient(this)

        val shouldShowLegacy = ExpirationConfiguration.isNewConfigEnabled &&
                legacyRecipient != null

        binding.outdatedBanner.isVisible = shouldShowLegacy
        if (shouldShowLegacy) {

            val txt = Phrase.from(applicationContext, R.string.disappearingMessagesLegacy)
                .put(NAME_KEY, legacyRecipient!!.toShortString())
                .format()
            binding?.outdatedBannerTextView?.text = txt
        }
    }

    private fun setUpLinkPreviewObserver() {
        if (!textSecurePreferences.isLinkPreviewsEnabled()) {
            linkPreviewViewModel.onUserCancel(); return
        }
        linkPreviewViewModel.linkPreviewState.observe(this) { previewState: LinkPreviewState? ->
            if (previewState == null) return@observe
            when {
                previewState.isLoading -> {
                    binding.inputBar.draftLinkPreview()
                }
                previewState.linkPreview.isPresent -> {
                    binding.inputBar.updateLinkPreviewDraft(glide, previewState.linkPreview.get())
                }
                else -> {
                    binding.inputBar.cancelLinkPreviewDraft()
                }
            }
        }
    }

    private fun setUpUiStateObserver() {
        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect { uiState ->
                uiState.uiMessages.firstOrNull()?.let {
                    Toast.makeText(this@ConversationActivityV2, it.message, Toast.LENGTH_LONG).show()
                    viewModel.messageShown(it.id)
                }
                if (uiState.isMessageRequestAccepted == true) {
                    binding.messageRequestBar.visibility = View.GONE
                }
                if (!uiState.conversationExists && !isFinishing) {
                    // Conversation should be deleted now
                    finish()
                }

                // show or hide the text input
                binding.inputBar.isGone = uiState.hideInputBar

                // show or hide loading indicator
                binding.loader.isVisible = uiState.showLoader
            }
        }
    }

    private fun scrollToFirstUnreadMessageIfNeeded(isFirstLoad: Boolean = false, shouldHighlight: Boolean = false): Int {
        val lastSeenTimestamp = threadDb.getLastSeenAndHasSent(viewModel.threadId).first()
        val lastSeenItemPosition = adapter.findLastSeenItemPosition(lastSeenTimestamp) ?: return -1

        // If this is triggered when first opening a conversation then we want to position the top
        // of the first unread message in the middle of the screen
        if (isFirstLoad && !reverseMessageList) {
            layoutManager?.scrollToPositionWithOffset(lastSeenItemPosition, ((layoutManager?.height ?: 0) / 2))
            if (shouldHighlight) { highlightViewAtPosition(lastSeenItemPosition) }
            return lastSeenItemPosition
        }

        if (lastSeenItemPosition <= 3) { return lastSeenItemPosition }

        binding.conversationRecyclerView.scrollToPosition(lastSeenItemPosition)
        return lastSeenItemPosition
    }

    private fun highlightViewAtPosition(position: Int) {
        binding.conversationRecyclerView.post {
            (layoutManager?.findViewByPosition(position) as? VisibleMessageView)?.playHighlight()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val recipient = viewModel.recipient ?: return false
        if (!viewModel.isMessageRequestThread) {
            ConversationMenuHelper.onPrepareOptionsMenu(
                menu,
                menuInflater,
                recipient,
                this
            )
        }
        maybeUpdateToolbar(recipient)
        return true
    }

    override fun onDestroy() {
        if(::binding.isInitialized) {
            viewModel.saveDraft(binding.inputBar.text.trim())
            cancelVoiceMessage()
            tearDownRecipientObserver()
        }
        super.onDestroy()
    }
    // endregion

    // region Animation & Updating
    override fun onModified(recipient: Recipient) {
        viewModel.updateRecipient()

        runOnUiThread {
            val threadRecipient = viewModel.recipient ?: return@runOnUiThread
            if (threadRecipient.isContactRecipient) {
                binding.blockedBanner.isVisible = threadRecipient.isBlocked
            }
            setUpMessageRequestsBar()
            invalidateOptionsMenu()
            updateSendAfterApprovalText()
            showOrHideInputIfNeeded()

            maybeUpdateToolbar(threadRecipient)
        }
    }

    private fun maybeUpdateToolbar(recipient: Recipient) {
        binding.toolbarContent.update(recipient, viewModel.openGroup, viewModel.expirationConfiguration)
    }

    private fun updateSendAfterApprovalText() {
        binding.textSendAfterApproval.isVisible = viewModel.showSendAfterApprovalText
    }

    private fun showOrHideInputIfNeeded() {
        binding.inputBar.showInput = viewModel.recipient?.takeIf { it.isClosedGroupRecipient }
            ?.run { address.toGroupString().let(groupDb::getGroup).orNull()?.isActive == true }
            ?: true
    }

    private fun setUpMessageRequestsBar() {
        binding.inputBar.showMediaControls = !isOutgoingMessageRequestThread()
        binding.messageRequestBar.isVisible = isIncomingMessageRequestThread()
        binding.acceptMessageRequestButton.setOnClickListener {
            acceptMessageRequest()
        }
        binding.messageRequestBlock.setOnClickListener {
            block(deleteThread = true)
        }
        binding.declineMessageRequestButton.setOnClickListener {
            fun doDecline() {
                viewModel.declineMessageRequest()
                lifecycleScope.launch(Dispatchers.IO) {
                    ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@ConversationActivityV2)
                }
                finish()
            }

            showSessionDialog {
                title(R.string.delete)
                text(resources.getString(R.string.messageRequestsDelete))
                dangerButton(R.string.delete) { doDecline() }
                button(R.string.cancel)
            }
        }
    }

    private fun acceptMessageRequest() {
        binding.messageRequestBar.isVisible = false
        viewModel.acceptMessageRequest()

        lifecycleScope.launch(Dispatchers.IO) {
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@ConversationActivityV2)
        }
    }

    private fun isOutgoingMessageRequestThread(): Boolean = viewModel.recipient?.run {
        !isGroupRecipient && !isLocalNumber &&
        !(hasApprovedMe() || viewModel.hasReceived())
    } ?: false

    private fun isIncomingMessageRequestThread(): Boolean = viewModel.recipient?.run {
        !isGroupRecipient && !isApproved && !isLocalNumber &&
        !threadDb.getLastSeenAndHasSent(viewModel.threadId).second() && threadDb.getMessageCount(viewModel.threadId) > 0
    } ?: false

    override fun inputBarEditTextContentChanged(newContent: CharSequence) {
        val inputBarText = binding.inputBar.text // TODO check if we should be referencing newContent here instead
        if (textSecurePreferences.isLinkPreviewsEnabled()) {
            linkPreviewViewModel.onTextChanged(this, inputBarText, 0, 0)
        }
        if (LinkPreviewUtil.findWhitelistedUrls(newContent.toString()).isNotEmpty()
            && !textSecurePreferences.isLinkPreviewsEnabled() && !textSecurePreferences.hasSeenLinkPreviewSuggestionDialog()) {
            LinkPreviewDialog {
                setUpLinkPreviewObserver()
                linkPreviewViewModel.onEnabled()
                linkPreviewViewModel.onTextChanged(this, inputBarText, 0, 0)
            }.show(supportFragmentManager, "Link Preview Dialog")
            textSecurePreferences.setHasSeenLinkPreviewSuggestionDialog()
        }
    }

    override fun toggleAttachmentOptions() {
        val targetAlpha = if (isShowingAttachmentOptions) 0.0f else 1.0f
        val allButtonContainers = listOfNotNull(
            binding.cameraButtonContainer,
            binding.libraryButtonContainer,
            binding.documentButtonContainer,
            binding.gifButtonContainer
        )
        val isReversed = isShowingAttachmentOptions // Run the animation in reverse
        val count = allButtonContainers.size
        allButtonContainers.indices.forEach { index ->
            val view = allButtonContainers[index]
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.alpha, targetAlpha)
            animation.duration = 250L
            animation.startDelay = if (isReversed) 50L * (count - index.toLong()) else 50L * index.toLong()
            animation.addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
            animation.start()
        }
        isShowingAttachmentOptions = !isShowingAttachmentOptions
        val allButtons = listOf( cameraButton, libraryButton, documentButton, gifButton )
        allButtons.forEach { it.snIsEnabled = isShowingAttachmentOptions }
    }

    override fun showVoiceMessageUI() {
        binding.inputBarRecordingView.show(lifecycleScope)
        binding.inputBar.alpha = 0.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        animation.duration = SHOW_HIDE_VOICE_UI_DURATION_MS
        animation.addUpdateListener { animator ->
            binding.inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun expandVoiceMessageLockView() {
        val lockView = binding.inputBarRecordingView.lockView
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.10f)
        animation.duration = ANIMATE_LOCK_DURATION_MS
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun collapseVoiceMessageLockView() {
        val lockView = binding.inputBarRecordingView.lockView
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.0f)
        animation.duration = ANIMATE_LOCK_DURATION_MS
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun hideVoiceMessageUI() {
        val chevronImageView = binding.inputBarRecordingView.chevronImageView
        val slideToCancelTextView = binding.inputBarRecordingView.slideToCancelTextView
        listOf( chevronImageView, slideToCancelTextView ).forEach { view ->
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.translationX, 0.0f)
            animation.duration = ANIMATE_LOCK_DURATION_MS
            animation.addUpdateListener { animator ->
                view.translationX = animator.animatedValue as Float
            }
            animation.start()
        }
        binding.inputBarRecordingView.hide()
    }

    override fun handleVoiceMessageUIHidden() {
        val inputBar = binding.inputBar
        inputBar.alpha = 1.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        animation.duration = SHOW_HIDE_VOICE_UI_DURATION_MS
        animation.addUpdateListener { animator ->
            inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun handleRecyclerViewScrolled() {
        // Note: The typing indicate is whether the other person / other people are typing - it has
        // nothing to do with the IME keyboard state.
        val wasTypingIndicatorVisibleBefore = binding.typingIndicatorViewContainer.isVisible
        binding.typingIndicatorViewContainer.isVisible = wasTypingIndicatorVisibleBefore && isScrolledToBottom

        showScrollToBottomButtonIfApplicable()
        val maybeTargetVisiblePosition = if (reverseMessageList) layoutManager?.findFirstVisibleItemPosition() else layoutManager?.findLastVisibleItemPosition()
        val targetVisiblePosition = maybeTargetVisiblePosition ?: RecyclerView.NO_POSITION
        if (!firstLoad.get() && targetVisiblePosition != RecyclerView.NO_POSITION) {
            adapter.getTimestampForItemAt(targetVisiblePosition)?.let { visibleItemTimestamp ->
                bufferedLastSeenChannel.trySend(visibleItemTimestamp).apply {
                    if (isFailure) Log.e(TAG, "trySend failed", exceptionOrNull())
                }
            }
        }

        if (reverseMessageList) {
            unreadCount = min(unreadCount, targetVisiblePosition).coerceAtLeast(0)
        } else {
            val layoutUnreadCount = layoutManager?.let { (it.itemCount - 1) - it.findLastVisibleItemPosition() }
                ?: RecyclerView.NO_POSITION
            unreadCount = min(unreadCount, layoutUnreadCount).coerceAtLeast(0)
        }
        updateUnreadCountIndicator()
    }

    // Update placeholder / control messages in a conversation
    private fun updatePlaceholder() {
        val recipient = viewModel.recipient ?: return Log.w("Loki", "recipient was null in placeholder update")
        val blindedRecipient = viewModel.blindedRecipient
        val openGroup = viewModel.openGroup

        // Get the correct placeholder text for this type of empty conversation
        val txtCS: CharSequence = when {
            // note to self
            recipient.isLocalNumber -> getString(R.string.noteToSelfEmpty)

            // If this is a community which we cannot write to
            openGroup != null && !openGroup.canWrite -> {
                Phrase.from(applicationContext, R.string.conversationsEmpty)
                    .put(CONVERSATION_NAME_KEY, openGroup.name)
                    .format()
            }

            // If we're trying to message someone who has blocked community message requests
            blindedRecipient?.blocksCommunityMessageRequests == true -> {
                Phrase.from(applicationContext, R.string.messageRequestsTurnedOff)
                    .put(NAME_KEY, recipient.toShortString())
                    .format()
            }

            // 10n1 and groups
            recipient.is1on1 || recipient.isGroupRecipient -> {
                Phrase.from(applicationContext, R.string.groupNoMessages)
                    .put(GROUP_NAME_KEY, recipient.toShortString())
                    .format()
            }

            else -> {
                Log.w(TAG, "Something else happened in updatePlaceholder - we're not sure what.")
                ""
            }
        }

        val showPlaceholder = adapter.itemCount == 0
        binding.placeholderText.isVisible = showPlaceholder
        if (showPlaceholder) {
            binding.placeholderText.text = txtCS
        }
    }

    private fun showScrollToBottomButtonIfApplicable() {
        binding.scrollToBottomButton.isVisible = !emojiPickerVisible && !isScrolledToBottom && adapter.itemCount > 0
    }

    private fun updateUnreadCountIndicator() {
        val formattedUnreadCount = if (unreadCount < 10000) unreadCount.toString() else "9999+"
        binding.unreadCountTextView.text = formattedUnreadCount
        val textSize = if (unreadCount < 10000) 12.0f else 9.0f
        binding.unreadCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
        binding.unreadCountTextView.setTypeface(Typeface.DEFAULT, if (unreadCount < 100) Typeface.BOLD else Typeface.NORMAL)
        binding.unreadCountIndicator.isVisible = (unreadCount != 0)
    }

    // endregion

    // region Interaction
    override fun onDisappearingMessagesClicked() {
        viewModel.recipient?.let { showDisappearingMessages(it) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return false
        }
        return viewModel.recipient?.let { recipient ->
            ConversationMenuHelper.onOptionItemSelected(this, item, recipient)
        } ?: false
    }

    override fun block(deleteThread: Boolean) {
        val recipient = viewModel.recipient ?: return Log.w("Loki", "Recipient was null for block action")
        showSessionDialog {
            title(R.string.block)
            text(
                Phrase.from(context, R.string.blockDescription)
                .put(NAME_KEY, recipient.toShortString())
                .format()
            )
            dangerButton(R.string.block, R.string.AccessibilityId_blockConfirm) {
                viewModel.block()

                // Block confirmation toast added as per SS-64
                val txt = Phrase.from(context, R.string.blockBlockedUser).put(NAME_KEY, recipient.toShortString()).format().toString()
                Toast.makeText(context, txt, Toast.LENGTH_LONG).show()

                if (deleteThread) {
                    viewModel.deleteThread()
                    finish()
                }
            }
            cancelButton()
        }
    }

    override fun copyAccountID(accountId: String) {
        val clip = ClipData.newPlainText("Account ID", accountId)
        val manager = getSystemService(PassphraseRequiredActionBarActivity.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    override fun copyOpenGroupUrl(thread: Recipient) {
        if (!thread.isCommunityRecipient) { return }

        val threadId = threadDb.getThreadIdIfExistsFor(thread)
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId) ?: return

        val clip = ClipData.newPlainText("Community URL", openGroup.joinURL)
        val manager = getSystemService(PassphraseRequiredActionBarActivity.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    override fun showDisappearingMessages(thread: Recipient) {
        if (thread.isClosedGroupRecipient) {
            groupDb.getGroup(thread.address.toGroupString()).orNull()?.run { if (!isActive) return }
        }
        Intent(this, DisappearingMessagesActivity::class.java)
            .apply { putExtra(DisappearingMessagesActivity.THREAD_ID, viewModel.threadId) }
            .also { show(it, true) }
    }

    override fun unblock() {
        val recipient = viewModel.recipient ?: return Log.w("Loki", "Recipient was null for unblock action")

        if (!recipient.isContactRecipient) {
            return Log.w("Loki", "Cannot unblock a user who is not a contact recipient - aborting unblock attempt.")
        }

        showSessionDialog {
            title(R.string.blockUnblock)
            text(
                Phrase.from(context, R.string.blockUnblockName)
                    .put(NAME_KEY, recipient.toShortString())
                    .format()
            )
            dangerButton(R.string.blockUnblock, R.string.AccessibilityId_unblockConfirm) { viewModel.unblock() }
            cancelButton()
        }
    }

    // `position` is the adapter position; not the visual position
    private fun handlePress(message: MessageRecord, position: Int, view: VisibleMessageView, event: MotionEvent) {
        val actionMode = this.actionMode
        if (actionMode != null) {
            onDeselect(message, position, actionMode)
        } else {
            // NOTE: We have to use onContentClick (rather than a click listener directly on
            // the view) so as to not interfere with all the other gestures. Do not add
            // onClickListeners directly to message content views!
            view.onContentClick(event)
        }
    }

    private fun onDeselect(message: MessageRecord, position: Int, actionMode: ActionMode) {
        adapter.toggleSelection(message, position)
        val actionModeCallback = ConversationActionModeCallback(adapter, viewModel.threadId, this)
        actionModeCallback.delegate = this
        actionModeCallback.updateActionModeMenu(actionMode.menu)
        if (adapter.selectedItems.isEmpty()) {
            actionMode.finish()
            this.actionMode = null
        }
    }

    // `position` is the adapter position; not the visual position
    private fun handleSwipeToReply(message: MessageRecord) {
        if (message.isOpenGroupInvitation) return
        val recipient = viewModel.recipient ?: return
        binding.inputBar.draftQuote(recipient, message, glide)
    }

    // `position` is the adapter position; not the visual position
    private fun selectMessage(message: MessageRecord, position: Int) {
        val actionMode = this.actionMode
        val actionModeCallback = ConversationActionModeCallback(adapter, viewModel.threadId, this)
        actionModeCallback.delegate = this
        searchViewItem?.collapseActionView()
        if (actionMode == null) { // Nothing should be selected if this is the case
            adapter.toggleSelection(message, position)
            this.actionMode = startActionMode(actionModeCallback, ActionMode.TYPE_PRIMARY)
        } else {
            adapter.toggleSelection(message, position)
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        }
    }

    private fun showConversationReaction(message: MessageRecord, messageView: View) {
        val messageContentView = when(messageView){
            is VisibleMessageView -> messageView.messageContentView
            is ControlMessageView -> messageView.controlContentView
            else -> null
        } ?: return Log.w(TAG, "Failed to show reaction because the messageRecord is not of a known type: $messageView")

        val messageContentBitmap = try {
            messageContentView.drawToBitmap()
        } catch (e: Exception) {
            Log.e("Loki", "Failed to show emoji picker", e)
            return
        }
        emojiPickerVisible = true
        ViewUtil.hideKeyboard(this, messageView)
        binding.reactionsShade.isVisible = true
        binding.scrollToBottomButton.isVisible = false
        binding.conversationRecyclerView.suppressLayout(true)
        reactionDelegate.setOnActionSelectedListener(ReactionsToolbarListener(message))
        reactionDelegate.setOnHideListener(object: ConversationReactionOverlay.OnHideListener {
            override fun startHide() {
                emojiPickerVisible = false
                binding.reactionsShade.let {
                    ViewUtil.fadeOut(it, resources.getInteger(R.integer.reaction_scrubber_hide_duration), View.GONE)
                }
                showScrollToBottomButtonIfApplicable()
            }

            override fun onHide() {
                binding.conversationRecyclerView.suppressLayout(false)

                WindowUtil.setLightStatusBarFromTheme(this@ConversationActivityV2);
                WindowUtil.setLightNavigationBarFromTheme(this@ConversationActivityV2);
            }

        })
        val topLeft = intArrayOf(0, 0).also { messageContentView.getLocationInWindow(it) }
        val selectedConversationModel = SelectedConversationModel(
            messageContentBitmap,
            topLeft[0].toFloat(),
            topLeft[1].toFloat(),
            messageContentView.width,
            message.isOutgoing,
            messageContentView
        )
        reactionDelegate.show(this, message, selectedConversationModel, viewModel.blindedPublicKey)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return reactionDelegate.applyTouchEvent(ev) || super.dispatchTouchEvent(ev)
    }

    override fun onReactionSelected(messageRecord: MessageRecord, emoji: String) {
        reactionDelegate.hide()
        val oldRecord = messageRecord.reactions.find { it.author == textSecurePreferences.getLocalNumber() }
        if (oldRecord != null && oldRecord.emoji == emoji) {
            sendEmojiRemoval(emoji, messageRecord)
        } else {
            sendEmojiReaction(emoji, messageRecord)
            RecentEmojiPageModel.onCodePointSelected(emoji) // Save to recently used reaction emojis
        }
    }

    // Method to add an emoji to a queue and remove it a short while later - this is used as a
    // rate-limiting mechanism and is called from the `sendEmojiReaction` method, below.

    fun canPerformEmojiReaction(timestamp: Long): Boolean {
        // If the emoji reaction queue is full..
        if (emojiRateLimiterQueue.size >= EMOJI_REACTIONS_ALLOWED_PER_MINUTE) {
            // ..grab the timestamp of the oldest emoji reaction.
            val headTimestamp = emojiRateLimiterQueue.peekFirst()
            if (headTimestamp == null) {
                Log.w(TAG, "Could not get emoji react head timestamp - should never happen, but we'll allow the emoji reaction.")
                return true
            }

            // With the queue full, if the earliest emoji reaction occurred less than 1 minute ago
            // then we reject it..
            if (System.currentTimeMillis() - headTimestamp <= ONE_MINUTE_IN_MILLISECONDS) {
                return false
            } else {
                // ..otherwise if the earliest emoji reaction was more than a minute ago we'll
                // remove that early reaction to move the timestamp at index 1 into index 0, add
                // our new timestamp and return true to accept the emoji reaction.
                emojiRateLimiterQueue.removeFirst()
                emojiRateLimiterQueue.addLast(timestamp)
                return true
            }
        } else {
            // If the queue isn't already full then we add the new timestamp to the back of the queue and allow the emoji reaction
            emojiRateLimiterQueue.addLast(timestamp)
            return true
        }
    }

    private fun sendEmojiReaction(emoji: String, originalMessage: MessageRecord) {
        // Only allow the emoji reaction if we aren't currently rate limited
        if (!canPerformEmojiReaction(System.currentTimeMillis())) {
            Toast.makeText(this, getString(R.string.emojiReactsCoolDown), Toast.LENGTH_SHORT).show()
            return
        }

        // Create the message
        val recipient = viewModel.recipient ?: return Log.w(TAG, "Could not locate recipient when sending emoji reaction")
        val reactionMessage = VisibleMessage()
        val emojiTimestamp = SnodeAPI.nowWithOffset
        reactionMessage.sentTimestamp = emojiTimestamp
        val author = textSecurePreferences.getLocalNumber()

        if (author == null) {
            Log.w(TAG, "Unable to locate local number when sending emoji reaction - aborting.")
            return
        } else {
            // Put the message in the database
            val reaction = ReactionRecord(
                messageId = originalMessage.id,
                isMms = originalMessage.isMms,
                author = author,
                emoji = emoji,
                count = 1,
                dateSent = emojiTimestamp,
                dateReceived = emojiTimestamp
            )
            reactionDb.addReaction(MessageId(originalMessage.id, originalMessage.isMms), reaction, false)

            val originalAuthor = if (originalMessage.isOutgoing) {
                fromSerialized(viewModel.blindedPublicKey ?: textSecurePreferences.getLocalNumber()!!)
            } else originalMessage.individualRecipient.address

            // Send it
            reactionMessage.reaction = Reaction.from(originalMessage.timestamp, originalAuthor.serialize(), emoji, true)
            if (recipient.isCommunityRecipient) {

                val messageServerId = lokiMessageDb.getServerID(originalMessage.id, !originalMessage.isMms) ?:
                    return Log.w(TAG, "Failed to find message server ID when adding emoji reaction")

                viewModel.openGroup?.let {
                    OpenGroupApi.addReaction(it.room, it.server, messageServerId, emoji)
                }
            } else {
                MessageSender.send(reactionMessage, recipient.address)
            }

            LoaderManager.getInstance(this).restartLoader(0, null, this)
        }
    }

    // Method to remove a emoji reaction from a message.
    // Note: We do not count emoji removal towards the emojiRateLimiterQueue.
    private fun sendEmojiRemoval(emoji: String, originalMessage: MessageRecord) {
        val recipient = viewModel.recipient ?: return
        val message = VisibleMessage()
        val emojiTimestamp = SnodeAPI.nowWithOffset
        message.sentTimestamp = emojiTimestamp
        val author = textSecurePreferences.getLocalNumber()

        if (author == null) {
            Log.w(TAG, "Unable to locate local number when removing emoji reaction - aborting.")
            return
        } else {
            reactionDb.deleteReaction(emoji, MessageId(originalMessage.id, originalMessage.isMms), author, false)

            val originalAuthor = if (originalMessage.isOutgoing) {
                fromSerialized(viewModel.blindedPublicKey ?: textSecurePreferences.getLocalNumber()!!)
            } else originalMessage.individualRecipient.address

            message.reaction = Reaction.from(originalMessage.timestamp, originalAuthor.serialize(), emoji, false)
            if (recipient.isCommunityRecipient) {

                val messageServerId = lokiMessageDb.getServerID(originalMessage.id, !originalMessage.isMms) ?:
                    return Log.w(TAG, "Failed to find message server ID when removing emoji reaction")

                viewModel.openGroup?.let {
                    OpenGroupApi.deleteReaction(it.room, it.server, messageServerId, emoji)
                }
            } else {
                MessageSender.send(message, recipient.address)
            }
            LoaderManager.getInstance(this).restartLoader(0, null, this)
        }
    }

    override fun onCustomReactionSelected(messageRecord: MessageRecord, hasAddedCustomEmoji: Boolean) {
        val oldRecord = messageRecord.reactions.find { record -> record.author == textSecurePreferences.getLocalNumber() }

        if (oldRecord != null && hasAddedCustomEmoji) {
            reactionDelegate.hide()
            sendEmojiRemoval(oldRecord.emoji, messageRecord)
        } else {
            reactionDelegate.hideForReactWithAny()

            ReactWithAnyEmojiDialogFragment
                .createForMessageRecord(messageRecord, reactWithAnyEmojiStartPage)
                .show(supportFragmentManager, "BOTTOM");
        }
    }

    override fun onReactWithAnyEmojiDialogDismissed() {
        reactionDelegate.hide()
    }

    override fun onReactWithAnyEmojiSelected(emoji: String, messageId: MessageId) {
        reactionDelegate.hide()
        val message = if (messageId.mms) {
            mmsDb.getMessageRecord(messageId.id)
        } else {
            smsDb.getMessageRecord(messageId.id)
        }
        val oldRecord = reactionDb.getReactions(messageId).find { it.author == textSecurePreferences.getLocalNumber() }
        if (oldRecord?.emoji == emoji) {
            sendEmojiRemoval(emoji, message)
        } else {
            sendEmojiReaction(emoji, message)
        }
    }

    override fun onRemoveReaction(emoji: String, messageId: MessageId) {
        val message = if (messageId.mms) {
            mmsDb.getMessageRecord(messageId.id)
        } else {
            smsDb.getMessageRecord(messageId.id)
        }
        sendEmojiRemoval(emoji, message)
    }

    /**
     * Called when the user is attempting to clear all instance of a specific emoji.
     */
    override fun onClearAll(emoji: String, messageId: MessageId) {
        viewModel.onEmojiClear(emoji, messageId)
    }

    override fun onMicrophoneButtonMove(event: MotionEvent) {
        val rawX = event.rawX
        val chevronImageView = binding.inputBarRecordingView.chevronImageView
        val slideToCancelTextView = binding.inputBarRecordingView.slideToCancelTextView
        if (rawX < screenWidth / 2) {
            val translationX = rawX - screenWidth / 2
            val sign = -1.0f
            val chevronDamping = 4.0f
            val labelDamping = 3.0f
            val chevronX = (chevronDamping * (sqrt(abs(translationX)) / sqrt(chevronDamping))) * sign
            val labelX = (labelDamping * (sqrt(abs(translationX)) / sqrt(labelDamping))) * sign
            chevronImageView.translationX = chevronX
            slideToCancelTextView.translationX = labelX
        } else {
            chevronImageView.translationX = 0.0f
            slideToCancelTextView.translationX = 0.0f
        }
        if (isValidLockViewLocation(event.rawX.roundToInt(), event.rawY.roundToInt())) {
            if (!isLockViewExpanded) {
                expandVoiceMessageLockView()
                isLockViewExpanded = true
            }
        } else {
            if (isLockViewExpanded) {
                collapseVoiceMessageLockView()
                isLockViewExpanded = false
            }
        }
    }

    override fun onMicrophoneButtonCancel(event: MotionEvent) {
        hideVoiceMessageUI()
    }

    override fun onMicrophoneButtonUp(event: MotionEvent) {
        val x = event.rawX.roundToInt()
        val y = event.rawY.roundToInt()
        val inputBar = binding.inputBar

        // Lock voice recording on if the button is released over the lock area AND the
        // voice recording has currently lasted for at least the time it takes to animate
        // the lock area into position. Without this time check we can accidentally lock
        // to recording audio on a quick tap as the lock area animates out from the record
        // audio message button and the pointer-up event catches it mid-animation.
        //
        // Further, by limiting this to AnimateLockDurationMS rather than our minimum voice
        // message length we get a fast, responsive UI that can lock 'straight away' - BUT
        // we then have to artificially bump the voice message duration because if you press
        // and slide to lock then release in one quick motion the pointer up event may be
        // less than our minimum voice message duration - so we'll bump our recorded duration
        // slightly to make sure we don't see the "Tap and hold to record..." toast when we
        // finish recording the message.
        if (isValidLockViewLocation(x, y) &&  inputBar.voiceMessageDurationMS >= ANIMATE_LOCK_DURATION_MS) {
            binding.inputBarRecordingView.lock()

            // Artificially bump message duration on lock if required
            if (inputBar.voiceMessageDurationMS < MINIMUM_VOICE_MESSAGE_DURATION_MS) {
                inputBar.voiceMessageDurationMS = MINIMUM_VOICE_MESSAGE_DURATION_MS
            }

            // If the user put the record audio button into the lock state then we are still recording audio
            binding.inputBar.voiceRecorderState = VoiceRecorderState.Recording
        }
        else // If the user didn't attempt to lock voice recording on..
        {
            // Regardless of where the button up event occurred we're now shutting down the recording (whether we send it or not)
            binding.inputBar.voiceRecorderState = VoiceRecorderState.ShuttingDownAfterRecord

            val rba = binding.inputBarRecordingView?.recordButtonOverlay
            if (rba != null) {
                val location = IntArray(2) { 0 }
                rba.getLocationOnScreen(location)
                val hitRect = Rect(location[0], location[1], location[0] + rba.width, location[1] + rba.height)

                // If the up event occurred over the record button overlay we send the voice message..
                if (hitRect.contains(x, y)) {
                    sendVoiceMessage()
                } else {
                    // ..otherwise if they've released off the button we'll cancel sending.
                    cancelVoiceMessage()
                }
            }
            else
            {
                // Just to cover all our bases, if for whatever reason the record button overlay was null we'll also cancel recording
                cancelVoiceMessage()
            }
        }
    }

    private fun isValidLockViewLocation(x: Int, y: Int): Boolean {
        // We can be anywhere above the lock view and a bit to the side of it (at most `lockViewHitMargin`
        // to the side)
        val lockViewLocation = IntArray(2) { 0 }
        binding.inputBarRecordingView.lockView.getLocationOnScreen(lockViewLocation)
        val hitRect = Rect(lockViewLocation[0] - lockViewHitMargin, 0,
            lockViewLocation[0] + binding.inputBarRecordingView.lockView.width + lockViewHitMargin, lockViewLocation[1] + binding.inputBarRecordingView.lockView.height)
        return hitRect.contains(x, y)
    }

    override fun scrollToMessageIfPossible(timestamp: Long) {
        val lastSeenItemPosition = adapter.getItemPositionForTimestamp(timestamp) ?: return
        binding.conversationRecyclerView?.scrollToPosition(lastSeenItemPosition)
    }

    override fun onReactionClicked(emoji: String, messageId: MessageId, userWasSender: Boolean) {
        val message = if (messageId.mms) {
            mmsDb.getMessageRecord(messageId.id)
        } else {
            smsDb.getMessageRecord(messageId.id)
        }
        if (userWasSender) {
            sendEmojiRemoval(emoji, message)
        } else {
            sendEmojiReaction(emoji, message)
        }
    }

    override fun onReactionLongClicked(messageId: MessageId, emoji: String?) {
        if (viewModel.recipient?.isGroupRecipient == true) {
            val isUserModerator = viewModel.openGroup?.let { openGroup ->
                val userPublicKey = textSecurePreferences.getLocalNumber() ?: return@let false
                OpenGroupManager.isUserModerator(this, openGroup.id, userPublicKey, viewModel.blindedPublicKey)
            } ?: false
            val fragment = ReactionsDialogFragment.create(messageId, isUserModerator, emoji)
            fragment.show(supportFragmentManager, null)
        }
    }

    override fun playVoiceMessageAtIndexIfPossible(indexInAdapter: Int) {
        if (!textSecurePreferences.autoplayAudioMessages()) return

        if (indexInAdapter < 0 || indexInAdapter >= adapter.itemCount) { return }
        val viewHolder = binding.conversationRecyclerView.findViewHolderForAdapterPosition(indexInAdapter) as? ConversationAdapter.VisibleMessageViewHolder ?: return
        viewHolder.view.playVoiceMessage()
    }

    override fun sendMessage() {
        val recipient = viewModel.recipient ?: return
        if (recipient.isContactRecipient && recipient.isBlocked) {
            BlockedDialog(recipient, this).show(supportFragmentManager, "Blocked Dialog")
            return
        }
        val sentMessageInfo = if (binding.inputBar.linkPreview != null || binding.inputBar.quote != null) {
            sendAttachments(listOf(), getMessageBody(), binding.inputBar.quote, binding.inputBar.linkPreview)
        } else {
            sendTextOnlyMessage()
        }

        // Jump to the newly sent message once it gets added
        if (sentMessageInfo != null) {
            messageToScrollAuthor.set(sentMessageInfo.first)
            messageToScrollTimestamp.set(sentMessageInfo.second)
        }
    }

    override fun commitInputContent(contentUri: Uri) {
        val recipient = viewModel.recipient ?: return
        val media = Media(contentUri, MediaUtil.getMimeType(this, contentUri)!!, 0, 0, 0, 0, Optional.absent(), Optional.absent())
        startActivityForResult(MediaSendActivity.buildEditorIntent(this, listOf( media ), recipient, getMessageBody()), PICK_FROM_LIBRARY)
    }

    private fun processMessageRequestApproval() {
        if (isIncomingMessageRequestThread()) {
            acceptMessageRequest()
        } else if (viewModel.recipient?.isApproved == false) {
            // edge case for new outgoing thread on new recipient without sending approval messages
            viewModel.setRecipientApproved()
        }
    }

    private fun sendTextOnlyMessage(hasPermissionToSendSeed: Boolean = false): Pair<Address, Long>? {
        val recipient = viewModel.recipient ?: return null
        val sentTimestamp = SnodeAPI.nowWithOffset
        processMessageRequestApproval()
        val text = getMessageBody()
        val userPublicKey = textSecurePreferences.getLocalNumber()
        val isNoteToSelf = (recipient.isContactRecipient && recipient.address.toString() == userPublicKey)
        if (seed in text && !isNoteToSelf && !hasPermissionToSendSeed) {
            showSessionDialog {
                title(R.string.warning)
                text(R.string.recoveryPasswordWarningSendDescription)
                button(R.string.send) { sendTextOnlyMessage(true) }
                cancelButton()
            }

            return null
        }
        // Create the message
        val message = VisibleMessage().applyExpiryMode(viewModel.threadId)
        message.sentTimestamp = sentTimestamp
        message.text = text
        val expiresInMillis = viewModel.expirationConfiguration?.expiryMode?.expiryMillis ?: 0
        val expireStartedAt = if (viewModel.expirationConfiguration?.expiryMode is ExpiryMode.AfterSend) {
            message.sentTimestamp
        } else 0
        val outgoingTextMessage = OutgoingTextMessage.from(message, recipient, expiresInMillis, expireStartedAt!!)
        // Clear the input bar
        binding.inputBar.text = ""
        binding.inputBar.cancelQuoteDraft()
        binding.inputBar.cancelLinkPreviewDraft()
        lifecycleScope.launch(Dispatchers.Default) {
            // Put the message in the database
            message.id = smsDb.insertMessageOutbox(
                viewModel.threadId,
                outgoingTextMessage,
                false,
                message.sentTimestamp!!,
                null,
                true
            )
            // Send it
            MessageSender.send(message, recipient.address)
        }
        // Send a typing stopped message
        ApplicationContext.getInstance(this).typingStatusSender.onTypingStopped(viewModel.threadId)
        return Pair(recipient.address, sentTimestamp)
    }

    private fun sendAttachments(
        attachments: List<Attachment>,
        body: String?,
        quotedMessage: MessageRecord? = binding.inputBar?.quote,
        linkPreview: LinkPreview? = null
    ): Pair<Address, Long>? {
        val recipient = viewModel.recipient ?: return null
        val sentTimestamp = SnodeAPI.nowWithOffset
        processMessageRequestApproval()
        // Create the message
        val message = VisibleMessage().applyExpiryMode(viewModel.threadId)
        message.sentTimestamp = sentTimestamp
        message.text = body
        val quote = quotedMessage?.let {
            val quotedAttachments = (it as? MmsMessageRecord)?.slideDeck?.asAttachments() ?: listOf()
            val sender = if (it.isOutgoing) {
                fromSerialized(viewModel.blindedPublicKey ?: textSecurePreferences.getLocalNumber()!!)
            } else it.individualRecipient.address
            QuoteModel(it.dateSent, sender, it.body, false, quotedAttachments)
        }
        val localQuote = quotedMessage?.let {
            val sender =
                if (it.isOutgoing) fromSerialized(textSecurePreferences.getLocalNumber()!!)
                else it.individualRecipient.address
            quote?.copy(author = sender)
        }
        val expiresInMs = viewModel.expirationConfiguration?.expiryMode?.expiryMillis ?: 0
        val expireStartedAtMs = if (viewModel.expirationConfiguration?.expiryMode is ExpiryMode.AfterSend) {
            sentTimestamp
        } else 0
        val outgoingTextMessage = OutgoingMediaMessage.from(message, recipient, attachments, localQuote, linkPreview, expiresInMs, expireStartedAtMs)
        // Clear the input bar
        binding.inputBar.text = ""
        binding.inputBar.cancelQuoteDraft()
        binding.inputBar.cancelLinkPreviewDraft()
        // Reset the attachment manager
        attachmentManager.clear()
        // Reset attachments button if needed
        if (isShowingAttachmentOptions) { toggleAttachmentOptions() }

        // do the heavy work in the bg
        lifecycleScope.launch(Dispatchers.IO) {
            // Put the message in the database
            message.id = mmsDb.insertMessageOutbox(
                outgoingTextMessage,
                viewModel.threadId,
                false,
                null,
                runThreadUpdate = true
            )
            // Send it
            MessageSender.send(message, recipient.address, attachments, quote, linkPreview)
        }

        // Send a typing stopped message
        ApplicationContext.getInstance(this).typingStatusSender.onTypingStopped(viewModel.threadId)
        return Pair(recipient.address, sentTimestamp)
    }

    private fun showGIFPicker() {
        val hasSeenGIFMetaDataWarning: Boolean = textSecurePreferences.hasSeenGIFMetaDataWarning()
        if (!hasSeenGIFMetaDataWarning) {
            showSessionDialog {
                title(R.string.giphyWarning)
                text(Phrase.from(context, R.string.giphyWarningDescription).put(APP_NAME_KEY, getString(R.string.app_name)).format())
                button(R.string.theContinue) {
                    textSecurePreferences.setHasSeenGIFMetaDataWarning()
                    selectGif()
                }
                cancelButton()
            }
        } else {
            selectGif()
        }
    }

    private fun selectGif() = AttachmentManager.selectGif(this, PICK_GIF)

    private fun showDocumentPicker() {
        AttachmentManager.selectDocument(this, PICK_DOCUMENT)
    }

    private fun pickFromLibrary() {
        val recipient = viewModel.recipient ?: return
        binding.inputBar.text?.trim()?.let { text ->
            AttachmentManager.selectGallery(this, PICK_FROM_LIBRARY, recipient, text)
        }
    }

    private fun showCamera() {
        attachmentManager.capturePhoto(this, TAKE_PHOTO, viewModel.recipient);
    }

    override fun onAttachmentChanged() {
        // Do nothing
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        val mediaPreppedListener = object : ListenableFuture.Listener<Boolean> {

            override fun onSuccess(result: Boolean?) {
                if (result == null) {
                    Log.w(TAG, "Media prepper returned a null result - bailing.")
                    return
                }

                // If the attachment was too large or MediaConstraints.isSatisfied failed for some
                // other reason then we reset the attachment manager & shown buttons then bail..
                if (!result) {
                    attachmentManager.clear()
                    if (isShowingAttachmentOptions) { toggleAttachmentOptions() }
                    return
                }

                // ..otherwise we can attempt to send the attachment(s).
                // Note: The only multi-attachment message type is when sending images - all others
                // attempt send the attachment immediately upon file selection.
                sendAttachments(attachmentManager.buildSlideDeck().asAttachments(), null)
            }

            override fun onFailure(e: ExecutionException?) {
                Toast.makeText(this@ConversationActivityV2, R.string.attachmentsErrorLoad, Toast.LENGTH_LONG).show()
            }
        }
        when (requestCode) {
            PICK_DOCUMENT -> {
                val uri = intent?.data ?: return
                prepMediaForSending(uri, AttachmentManager.MediaType.DOCUMENT).addListener(mediaPreppedListener)
            }
            PICK_GIF -> {
                intent ?: return
                val uri = intent.data ?: return
                val type = AttachmentManager.MediaType.GIF
                val width = intent.getIntExtra(GiphyActivity.EXTRA_WIDTH, 0)
                val height = intent.getIntExtra(GiphyActivity.EXTRA_HEIGHT, 0)
                prepMediaForSending(uri, type, width, height).addListener(mediaPreppedListener)
            }
            PICK_FROM_LIBRARY,
            TAKE_PHOTO -> {
                intent ?: return
                val body = intent.getStringExtra(MediaSendActivity.EXTRA_MESSAGE)
                val media = intent.getParcelableArrayListExtra<Media>(MediaSendActivity.EXTRA_MEDIA) ?: return
                val slideDeck = SlideDeck()
                for (item in media) {
                    when {
                        MediaUtil.isVideoType(item.mimeType) -> {
                            slideDeck.addSlide(VideoSlide(this, item.uri, 0, item.caption.orNull()))
                        }
                        MediaUtil.isGif(item.mimeType) -> {
                            slideDeck.addSlide(GifSlide(this, item.uri, 0, item.width, item.height, item.caption.orNull()))
                        }
                        MediaUtil.isImageType(item.mimeType) -> {
                            slideDeck.addSlide(ImageSlide(this, item.uri, 0, item.width, item.height, item.caption.orNull()))
                        }
                        else -> {
                            Log.d("Loki", "Asked to send an unexpected media type: '" + item.mimeType + "'. Skipping.")
                        }
                    }
                }
                sendAttachments(slideDeck.asAttachments(), body)
            }
            INVITE_CONTACTS -> {
                if (viewModel.recipient?.isCommunityRecipient != true) { return }
                val extras = intent?.extras ?: return
                if (!intent.hasExtra(selectedContactsKey)) { return }
                val selectedContacts = extras.getStringArray(selectedContactsKey)!!
                val recipients = selectedContacts.map { contact ->
                    Recipient.from(this, fromSerialized(contact), true)
                }
                viewModel.inviteContacts(recipients)
            }
        }
    }

    private fun prepMediaForSending(uri: Uri, type: AttachmentManager.MediaType): ListenableFuture<Boolean> {
        return prepMediaForSending(uri, type, null, null)
    }

    private fun prepMediaForSending(uri: Uri, type: AttachmentManager.MediaType, width: Int?, height: Int?): ListenableFuture<Boolean> {
        return attachmentManager.setMedia(glide, uri, type, MediaConstraints.getPushMediaConstraints(), width ?: 0, height ?: 0)
    }

    override fun startRecordingVoiceMessage() {
        if (Permissions.hasAll(this, Manifest.permission.RECORD_AUDIO)) {
            showVoiceMessageUI()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Allow the caller (us!) to define what should happen when the voice recording finishes.
            // Specifically in this instance, if we just tap the record audio button then by the time
            // we actually finish setting up and get here the recording has been cancelled and the voice
            // recorder state is Idle! As such we'll only tick the recorder state over to Recording if
            // we were still in the SettingUpToRecord state when we got here (i.e., the record voice
            // message button is still held or is locked to keep recording audio without being held).
            val callback: () -> Unit = {
                if (binding.inputBar.voiceRecorderState == VoiceRecorderState.SettingUpToRecord) {
                    binding.inputBar.voiceRecorderState = VoiceRecorderState.Recording
                }
            }
            audioRecorder.startRecording(callback)

            stopAudioHandler.postDelayed(stopVoiceMessageRecordingTask, 300000) // Limit voice messages to 5 minute each
        } else {
            Permissions.with(this)
                .request(Manifest.permission.RECORD_AUDIO)
                .withPermanentDenialDialog(Phrase.from(applicationContext, R.string.permissionsMicrophoneAccessRequired)
                    .put(APP_NAME_KEY, getString(R.string.app_name))
                    .format().toString())
                .execute()
        }
    }

    private fun informUserIfNetworkOrSessionNodePathIsInvalid() {

        // Check that we have a valid network network connection & inform the user if not
        val connectedToInternet = NetworkUtils.haveValidNetworkConnection(applicationContext)
        if (!connectedToInternet)
        {
            // TODO: Adjust to display error to user with official localised string when SES-2319 is addressed
            Log.e(TAG, "Cannot sent voice message - no network connection.")
        }

        // Check that we have a suite of Session Nodes to route through.
        // Note: We can have the entry node plus the 2 Session Nodes and the data _still_ might not
        // send due to any node flakiness - but without doing some manner of test-ping through
        // there's no way to test our client -> destination connectivity (unless we abuse the typing
        // indicators?)
        val paths = OnionRequestAPI.paths
        if (paths.isNullOrEmpty() || paths.count() != 2) {
            // TODO: Adjust to display error to user with official localised string when SES-2319 is addressed
            Log.e(TAG, "Cannot send voice message - bad Session Node path.")
        }
    }

    override fun sendVoiceMessage() {
        // When the record voice message button is released we always need to reset the UI and cancel
        // any further recording operation..
        hideVoiceMessageUI()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val future = audioRecorder.stopRecording()
        stopAudioHandler.removeCallbacks(stopVoiceMessageRecordingTask)

        // ..but we'll bail without sending the voice message & inform the user that they need to press and HOLD
        // the record voice message button if their message was less than 1 second long.
        val inputBar = binding.inputBar
        val voiceMessageDurationMS = inputBar.voiceMessageDurationMS

        // Now tear-down is complete we can move back into the idle state ready to record another voice message.
        // CAREFUL: This state must be set BEFORE we show any warning toast about short messages because it early
        // exits before transmitting the audio!
        inputBar.voiceRecorderState = VoiceRecorderState.Idle

        // Voice message too short? Warn with toast instead of sending.
        // Note: The 0L check prevents the warning toast being shown when leaving the conversation activity.
        if (voiceMessageDurationMS != 0L && voiceMessageDurationMS < MINIMUM_VOICE_MESSAGE_DURATION_MS) {
            Toast.makeText(this@ConversationActivityV2, R.string.messageVoiceErrorShort, Toast.LENGTH_SHORT).show()
            inputBar.voiceMessageDurationMS = 0L
            return
        }

        informUserIfNetworkOrSessionNodePathIsInvalid()
        // Note: We could return here if there was a network or node path issue, but instead we'll try
        // our best to send the voice message even if it might fail - because in that case it'll get put
        // into the draft database and can be retried when we regain network connectivity and a working
        // node path.

        // Attempt to send it the voice message
        future.addListener(object : ListenableFuture.Listener<Pair<Uri, Long>> {

            override fun onSuccess(result: Pair<Uri, Long>) {
                val audioSlide = AudioSlide(this@ConversationActivityV2, result.first, result.second, MediaTypes.AUDIO_AAC, true)
                val slideDeck = SlideDeck()
                slideDeck.addSlide(audioSlide)
                sendAttachments(slideDeck.asAttachments(), null)
            }

            override fun onFailure(e: ExecutionException) {
                Toast.makeText(this@ConversationActivityV2, R.string.audioUnableToRecord, Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun cancelVoiceMessage() {
        val inputBar = binding.inputBar

        hideVoiceMessageUI()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioRecorder.stopRecording()
        stopAudioHandler.removeCallbacks(stopVoiceMessageRecordingTask)

        // Note: The 0L check prevents the warning toast being shown when leaving the conversation activity
        val voiceMessageDuration = inputBar.voiceMessageDurationMS
        if (voiceMessageDuration != 0L && voiceMessageDuration < MINIMUM_VOICE_MESSAGE_DURATION_MS) {
            Toast.makeText(applicationContext, applicationContext.getString(R.string.messageVoiceErrorShort), Toast.LENGTH_SHORT).show()
            inputBar.voiceMessageDurationMS = 0L
        }

        // When tear-down is complete (via cancelling) we can move back into the idle state ready to record
        // another voice message.
        inputBar.voiceRecorderState = VoiceRecorderState.Idle
    }

    override fun selectMessages(messages: Set<MessageRecord>) {
        selectMessage(messages.first(), 0) //TODO: begin selection mode
    }

    // Note: The messages in the provided set may be a single message, or multiple if there are a
    // group of selected messages.
    override fun deleteMessages(messages: Set<MessageRecord>) {
        viewModel.handleMessagesDeletion(messages)
        endActionMode()
    }

    override fun banUser(messages: Set<MessageRecord>) {
        showSessionDialog {
            title(R.string.banUser)
            text(R.string.communityBanDescription)
            dangerButton(R.string.theContinue) { viewModel.banUser(messages.first().individualRecipient); endActionMode() }
            cancelButton(::endActionMode)
        }
    }

    override fun banAndDeleteAll(messages: Set<MessageRecord>) {
        showSessionDialog {
            title(R.string.banDeleteAll)
            text(R.string.communityBanDeleteDescription)
            dangerButton(R.string.theContinue) { viewModel.banAndDeleteAll(messages.first()); endActionMode() }
            cancelButton(::endActionMode)
        }
    }

    override fun copyMessages(messages: Set<MessageRecord>) {
        val sortedMessages = messages.sortedBy { it.dateSent }
        val messageSize = sortedMessages.size
        val builder = StringBuilder()
        val messageIterator = sortedMessages.iterator()
        while (messageIterator.hasNext()) {
            val message = messageIterator.next()
            val body = MentionUtilities.highlightMentions(
                text = message.body,
                formatOnly = true, // no styling here, only text formatting
                threadID = viewModel.threadId,
                context = this
            )

            if (TextUtils.isEmpty(body)) { continue }
            if (messageSize > 1) {
                val formattedTimestamp = DateUtils.getDisplayFormattedTimeSpanString(this, Locale.getDefault(), message.timestamp)
                builder.append("$formattedTimestamp: ")
            }
            builder.append(body)
            if (messageIterator.hasNext()) {
                builder.append('\n')
            }
        }
        if (builder.isNotEmpty() && builder[builder.length - 1] == '\n') {
            builder.deleteCharAt(builder.length - 1)
        }
        val result = builder.toString()
        if (TextUtils.isEmpty(result)) { return }
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("Message Content", result))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        endActionMode()
    }

    override fun copyAccountID(messages: Set<MessageRecord>) {
        val accountID = messages.first().individualRecipient.address.toString()
        val clip = ClipData.newPlainText("Account ID", accountID)
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        endActionMode()
    }

    override fun resyncMessage(messages: Set<MessageRecord>) {
        messages.iterator().forEach { messageRecord ->
            ResendMessageUtilities.resend(this, messageRecord, viewModel.blindedPublicKey, isResync = true)
        }
        endActionMode()
    }

    override fun resendMessage(messages: Set<MessageRecord>) {
        messages.iterator().forEach { messageRecord ->
            ResendMessageUtilities.resend(this, messageRecord, viewModel.blindedPublicKey)
        }
        endActionMode()
    }

    private val handleMessageDetail = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val message = result.data?.extras?.getLong(MESSAGE_TIMESTAMP)
            ?.let(mmsSmsDb::getMessageForTimestamp)

        val set = setOfNotNull(message)

        when (result.resultCode) {
            ON_REPLY -> reply(set)
            ON_RESEND -> resendMessage(set)
            ON_DELETE -> deleteMessages(set)
            ON_COPY -> copyMessages(set)
            ON_SAVE -> {
                if(message is MmsMessageRecord) saveAttachmentsIfPossible(setOf(message))
            }
        }
    }

    override fun showMessageDetail(messages: Set<MessageRecord>) {
        Intent(this, MessageDetailActivity::class.java)
            .apply { putExtra(MESSAGE_TIMESTAMP, messages.first().timestamp) }
            .let {
                handleMessageDetail.launch(it)
                overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
            }

        endActionMode()
    }

    private fun saveAttachments(message: MmsMessageRecord) {
        val attachments: List<SaveAttachmentTask.Attachment?> = Stream.of(message.slideDeck.slides)
            .filter { s: Slide -> s.uri != null && (s.hasImage() || s.hasVideo() || s.hasAudio() || s.hasDocument()) }
            .map { s: Slide -> SaveAttachmentTask.Attachment(s.uri!!, s.contentType, message.dateReceived, s.fileName.orNull()) }
            .toList()
        if (attachments.isNotEmpty()) {
            val saveTask = SaveAttachmentTask(this)
            saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, *attachments.toTypedArray())
            if (!message.isOutgoing) { sendMediaSavedNotification() }
            return
        }
        // Implied else that there were no attachment(s)
        Toast.makeText(this, resources.getString(R.string.attachmentsSaveError), Toast.LENGTH_LONG).show()
    }

    private fun hasPermission(permission: String): Boolean {
        val result = ContextCompat.checkSelfPermission(this, permission)
        return result == PackageManager.PERMISSION_GRANTED
    }

    override fun saveAttachmentsIfPossible(messages: Set<MessageRecord>) {
        val message = messages.first() as MmsMessageRecord

        // Note: The save option is only added to the menu in ConversationReactionOverlay.getMenuActionItems
        // if the attachment has finished downloading, so we don't really have to check for message.isMediaPending
        // here - but we'll do it anyway and bail should that be the case as a defensive programming strategy.
        if (message.isMediaPending) {
            Log.w(TAG, "Somehow we were asked to download an attachment before it had finished downloading - aborting download.")
            return
        }

        // Before saving an attachment, regardless of Android API version or permissions, we always want to ensure
        // that we've warned the user just _once_ that any attachments they save can be accessed by other apps.
        val haveWarned = TextSecurePreferences.getHaveWarnedUserAboutSavingAttachments(this)
        if (haveWarned) {
            // On Android versions below 29 we require the WRITE_EXTERNAL_STORAGE permission to save attachments.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                // Save the attachment(s) then bail if we already have permission to do so
                if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    saveAttachments(message)
                    return
                } else {
                    /* If we don't have the permission then do nothing - which means we continue on to the SaveAttachmentTask part below where we ask for permissions */
                }
            } else {
                // On more modern versions of Android on API 30+ WRITE_EXTERNAL_STORAGE is no longer used and we can just
                // save files to the public directories like "Downloads", "Pictures" etc.
                saveAttachments(message)
                return
            }
        }

        // ..otherwise we must ask for it first (only on Android APIs up to 28).
        SaveAttachmentTask.showOneTimeWarningDialogOrSave(this) {
            Permissions.with(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .maxSdkVersion(Build.VERSION_CODES.P) // P is 28
                .withPermanentDenialDialog(Phrase.from(applicationContext, R.string.permissionsStorageDeniedLegacy)
                    .put(APP_NAME_KEY, getString(R.string.app_name))
                    .format().toString())
                .onAnyDenied {
                    endActionMode()

                    // If permissions were denied inform the user that we can't proceed without them and offer to take the user to Settings
                    showSessionDialog {
                        title(R.string.permissionsRequired)

                        val txt = Phrase.from(applicationContext, R.string.permissionsStorageDeniedLegacy)
                            .put(APP_NAME_KEY, getString(R.string.app_name))
                            .format().toString()
                        text(txt)

                        // Take the user directly to the settings app for Session to grant the permission if they
                        // initially denied it but then have a change of heart when they realise they can't
                        // proceed without it.
                        dangerButton(R.string.theContinue) {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val uri = Uri.fromParts("package", packageName, null)
                            intent.setData(uri)
                            startActivity(intent)
                        }

                        button(R.string.cancel)
                    }
                }
                .onAllGranted {
                    endActionMode()
                    saveAttachments(message)
                }
                .execute()
        }
    }

    override fun reply(messages: Set<MessageRecord>) {
        val recipient = viewModel.recipient ?: return
        messages.firstOrNull()?.let { binding.inputBar.draftQuote(recipient, it, glide) }
        endActionMode()
    }

    override fun destroyActionMode() {
        this.actionMode = null
    }

    private fun sendScreenshotNotification() {
        val recipient = viewModel.recipient ?: return
        if (recipient.isGroupRecipient) return
        val kind = DataExtractionNotification.Kind.Screenshot()
        val message = DataExtractionNotification(kind)
        MessageSender.send(message, recipient.address)
    }

    private fun sendMediaSavedNotification() {
        val recipient = viewModel.recipient ?: return
        if (recipient.isGroupRecipient) { return }
        val timestamp = SnodeAPI.nowWithOffset
        val kind = DataExtractionNotification.Kind.MediaSaved(timestamp)
        val message = DataExtractionNotification(kind)
        MessageSender.send(message, recipient.address)
    }

    private fun endActionMode() {
        actionMode?.finish()
        actionMode = null
    }
    // endregion

    // region General
    private fun getMessageBody(): String {
        return mentionViewModel.normalizeMessageBody()
    }
    // endregion

    // region Search
    private fun setUpSearchResultObserver() {
        searchViewModel.searchResults.observe(this, Observer { result: SearchViewModel.SearchResult? ->
            if (result == null) return@Observer
            if (result.getResults().isNotEmpty()) {
                result.getResults()[result.position]?.let {
                    jumpToMessage(it.messageRecipient.address, it.sentTimestampMs, true) {
                        searchViewModel.onMissingResult() }
                }
            }

            binding.searchBottomBar.setData(result.position, result.getResults().size)
        })
    }

    fun onSearchOpened() {
        searchViewModel.onSearchOpened()
        binding.searchBottomBar.visibility = View.VISIBLE
        binding.searchBottomBar.setData(0, 0)
        binding.inputBar.visibility = View.INVISIBLE

    }

    fun onSearchClosed() {
        searchViewModel.onSearchClosed()
        binding.searchBottomBar.visibility = View.GONE
        binding.inputBar.visibility = View.VISIBLE
        adapter.onSearchQueryUpdated(null)
        invalidateOptionsMenu()
    }

    fun onSearchQueryUpdated(query: String) {
        searchViewModel.onQueryUpdated(query, viewModel.threadId)
        binding.searchBottomBar.showLoading()
        adapter.onSearchQueryUpdated(query)
    }

    override fun onSearchMoveUpPressed() {
        this.searchViewModel.onMoveUp()
    }

    override fun onSearchMoveDownPressed() {
        this.searchViewModel.onMoveDown()
    }

    private fun jumpToMessage(author: Address, timestamp: Long, highlight: Boolean, onMessageNotFound: Runnable?) {
        SimpleTask.run(lifecycle, {
            mmsSmsDb.getMessagePositionInConversation(viewModel.threadId, timestamp, author, reverseMessageList)
        }) { p: Int -> moveToMessagePosition(p, highlight, onMessageNotFound) }
    }

    private fun moveToMessagePosition(position: Int, highlight: Boolean, onMessageNotFound: Runnable?) {
        if (position >= 0) {
            binding.conversationRecyclerView.scrollToPosition(position)

            if (highlight) {
                runOnUiThread {
                    highlightViewAtPosition(position)
                }
            }
        } else {
            onMessageNotFound?.run()
        }
    }
    // endregion

    inner class ReactionsToolbarListener constructor(val message: MessageRecord) : OnActionSelectedListener {

        override fun onActionSelected(action: ConversationReactionOverlay.Action) {
            val selectedItems = setOf(message)
            when (action) {
                ConversationReactionOverlay.Action.REPLY -> reply(selectedItems)
                ConversationReactionOverlay.Action.RESYNC -> resyncMessage(selectedItems)
                ConversationReactionOverlay.Action.RESEND -> resendMessage(selectedItems)
                ConversationReactionOverlay.Action.DOWNLOAD -> saveAttachmentsIfPossible(selectedItems)
                ConversationReactionOverlay.Action.COPY_MESSAGE -> copyMessages(selectedItems)
                ConversationReactionOverlay.Action.VIEW_INFO -> showMessageDetail(selectedItems)
                ConversationReactionOverlay.Action.SELECT -> selectMessages(selectedItems)
                ConversationReactionOverlay.Action.DELETE -> deleteMessages(selectedItems)
                ConversationReactionOverlay.Action.BAN_AND_DELETE_ALL -> banAndDeleteAll(selectedItems)
                ConversationReactionOverlay.Action.BAN_USER -> banUser(selectedItems)
                ConversationReactionOverlay.Action.COPY_ACCOUNT_ID -> copyAccountID(selectedItems)
            }
        }
    }

    // AdapterDataObserver implementation to scroll us to the bottom of the ConversationRecyclerView
    // when we're already near the bottom and we send or receive a message.
    inner class ConversationAdapterDataObserver(val recyclerView: ConversationRecyclerView, val adapter: ConversationAdapter) : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            super.onChanged()
            if (recyclerView.isScrolledToWithin30dpOfBottom) {
                // Note: The adapter itemCount is zero based - so calling this with the itemCount in
                // a non-zero based manner scrolls us to the bottom of the last message (including
                // to the bottom of long messages as required by Jira SES-789 / GitHub 1364).
                recyclerView.smoothScrollToPosition(adapter.itemCount)
            }
        }
    }

}