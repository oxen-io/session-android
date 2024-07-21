package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewInputBarBinding
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.components.LinkPreviewDraftView
import org.thoughtcrime.securesms.conversation.v2.components.LinkPreviewDraftViewDelegate
import org.thoughtcrime.securesms.conversation.v2.messages.QuoteView
import org.thoughtcrime.securesms.conversation.v2.messages.QuoteViewDelegate
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.addTextChangedListener
import org.thoughtcrime.securesms.util.contains

// Enums to keep track of the state of our voice recording mechanism as the user can
// manipulate the UI faster than we can setup & teardown.
enum class VoiceRecorderState {
    Idle,
    SettingUpToRecord,
    Recording,
    ShuttingDownAfterRecord
}

@SuppressLint("ClickableViewAccessibility")
class InputBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(
    context,
    attrs,
    defStyleAttr
), InputBarEditTextDelegate,
    QuoteViewDelegate,
    LinkPreviewDraftViewDelegate,
    TextView.OnEditorActionListener {

    private var binding: ViewInputBarBinding = ViewInputBarBinding.inflate(LayoutInflater.from(context), this, true)
    private var linkPreviewDraftView: LinkPreviewDraftView? = null
    private var quoteView: QuoteView? = null
    var delegate: InputBarDelegate? = null
    var quote: MessageRecord? = null
    var linkPreview: LinkPreview? = null
    var showInput: Boolean = true
        set(value) { field = value; showOrHideInputIfNeeded() }
    var showMediaControls: Boolean = true
        set(value) {
            field = value
            showOrHideMediaControlsIfNeeded()
            binding.inputBarEditText.showMediaControls = value
        }

    var text: String
        get() = binding.inputBarEditText.text?.toString() ?: ""
        set(value) { binding.inputBarEditText.setText(value) }

    // Keep track of when the user pressed the record voice message button, the duration that
    // they held record, and the current audio recording mechanism state.
    private var voiceMessageStartMS = 0L
    var voiceMessageDurationMS = 0L
    var voiceRecorderState = VoiceRecorderState.Idle

    private val attachmentsButton = InputBarButton(context, R.drawable.ic_plus_24).apply { contentDescription = context.getString(R.string.AccessibilityId_attachments_button)}
    val microphoneButton = InputBarButton(context, R.drawable.ic_microphone).apply { contentDescription = context.getString(R.string.AccessibilityId_microphone_button)}
    private val sendButton = InputBarButton(context, R.drawable.ic_arrow_up, true).apply { contentDescription = context.getString(R.string.AccessibilityId_send_message_button)}

    init {
        // Attachments button
        binding.attachmentsButtonContainer.addView(attachmentsButton)
        attachmentsButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        attachmentsButton.onPress = { toggleAttachmentOptions() }

        // Microphone button
        binding.microphoneOrSendButtonContainer.addView(microphoneButton)
        microphoneButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        microphoneButton.onMove = { delegate?.onMicrophoneButtonMove(it) }
        microphoneButton.onCancel = { delegate?.onMicrophoneButtonCancel(it) }

        // Use a separate 'raw' OnTouchListener to record the microphone button down/up timestamps because
        // they don't get delayed by any multi-threading or delegates which throw off the timestamp accuracy.
        // For example: If we bind something to `microphoneButton.onPress` and also log something in
        // `microphoneButton.onUp` and tap the button then the logged output order is onUp and THEN onPress!
        microphoneButton.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (!microphoneButton.snIsEnabled) return true

                // We only handle single finger touch events so just consume the event and bail if there are more
                if (event.pointerCount > 1) return true

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Only start spinning up the voice recorder if we're not already recording, setting up, or tearing down
                        if (voiceRecorderState == VoiceRecorderState.Idle) {
                            // Take note of when we start recording so we can figure out how long the record button was held for
                            voiceMessageStartMS = System.currentTimeMillis()

                            // We are now setting up to record, and when we actually start recording then
                            // AudioRecorder.startRecording will move us into the Recording state.
                            voiceRecorderState = VoiceRecorderState.SettingUpToRecord
                            startRecordingVoiceMessage()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // Work out how long the record audio button was held for
                        voiceMessageDurationMS = System.currentTimeMillis() - voiceMessageStartMS;

                        // Regardless of our current recording state we'll always call the onMicrophoneButtonUp method
                        // and let the logic in that take the appropriate action as we cannot guarantee that letting
                        // go of the record button should always stop recording audio because the user may have moved
                        // the button into the 'locked' state so they don't have to keep it held down to record a voice
                        // message.
                        // Also: We need to tear down the voice recorder if it has been recording and is now stopping.
                        delegate?.onMicrophoneButtonUp(event)
                    }
                }

                // Return false to propagate the event rather than consuming it
                return false
            }
        })

        // Send button
        binding.microphoneOrSendButtonContainer.addView(sendButton)
        sendButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        sendButton.isVisible = false
        sendButton.onUp = { e ->
            if (sendButton.contains(PointF(e.x, e.y))) {
                delegate?.sendMessage()
            }
        }

        // Edit text
        binding.inputBarEditText.setOnEditorActionListener(this)
        if (TextSecurePreferences.isEnterSendsEnabled(context)) {
            binding.inputBarEditText.imeOptions = EditorInfo.IME_ACTION_SEND
            binding.inputBarEditText.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            binding.inputBarEditText.imeOptions = EditorInfo.IME_ACTION_NONE
            binding.inputBarEditText.inputType =
                binding.inputBarEditText.inputType
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val incognitoFlag = if (TextSecurePreferences.isIncognitoKeyboardEnabled(context)) 16777216 else 0
        binding.inputBarEditText.imeOptions = binding.inputBarEditText.imeOptions or incognitoFlag // Always use incognito keyboard if setting enabled
        binding.inputBarEditText.delegate = this
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (v === binding.inputBarEditText && actionId == EditorInfo.IME_ACTION_SEND) {
            // same as pressing send button
            delegate?.sendMessage()
            return true
        }
        return false
    }

    override fun inputBarEditTextContentChanged(text: CharSequence) {
        microphoneButton.isVisible = text.trim().isEmpty()
        sendButton.isVisible = microphoneButton.isGone
        delegate?.inputBarEditTextContentChanged(text)
    }

    override fun inputBarEditTextHeightChanged(newValue: Int) { }

    override fun commitInputContent(contentUri: Uri) { delegate?.commitInputContent(contentUri) }

    private fun toggleAttachmentOptions() { delegate?.toggleAttachmentOptions() }

    private fun startRecordingVoiceMessage() { delegate?.startRecordingVoiceMessage() }

    fun draftQuote(thread: Recipient, message: MessageRecord, glide: GlideRequests) {
        quoteView?.let(binding.inputBarAdditionalContentContainer::removeView)

        quote = message

        // If we already have a link preview View then clear the 'additional content' layout so that
        // our quote View is always the first element (i.e., at the top of the reply).
        if (linkPreview != null && linkPreviewDraftView != null) {
            binding.inputBarAdditionalContentContainer.removeAllViews()
        }

        // Inflate quote View with typed array here
        val layout = LayoutInflater.from(context).inflate(R.layout.view_quote_draft, binding.inputBarAdditionalContentContainer, false)
        quoteView = layout.findViewById<QuoteView>(R.id.mainQuoteViewContainer).also {
            it.delegate = this
            binding.inputBarAdditionalContentContainer.addView(layout)
            val attachments = (message as? MmsMessageRecord)?.slideDeck
            val sender = if (message.isOutgoing) TextSecurePreferences.getLocalNumber(context)!! else message.individualRecipient.address.serialize()
            it.bind(sender, message.body, attachments, thread, true, message.isOpenGroupInvitation, message.threadId, false, glide)
        }

        // Before we request a layout update we'll add back any LinkPreviewDraftView that might
        // exist - as this goes into the LinearLayout second it will be below the quote View.
        if (linkPreview != null && linkPreviewDraftView != null) {
            binding.inputBarAdditionalContentContainer.addView(linkPreviewDraftView)
        }
        requestLayout()
    }

    override fun cancelQuoteDraft() {
        binding.inputBarAdditionalContentContainer.removeView(quoteView)
        quote = null
        quoteView = null
        requestLayout()
    }

    fun draftLinkPreview() {
        // As `draftLinkPreview` is called before `updateLinkPreview` when we modify a URI in a
        // message we'll bail early if a link preview View already exists and just let
        // `updateLinkPreview` get called to update the existing View.
        if (linkPreview != null && linkPreviewDraftView != null) return
        linkPreviewDraftView?.let(binding.inputBarAdditionalContentContainer::removeView)
        linkPreviewDraftView = LinkPreviewDraftView(context).also { it.delegate = this }

        // Add the link preview View. Note: If there's already a quote View in the 'additional
        // content' container then this preview View will be added after / below it - which is fine.
        binding.inputBarAdditionalContentContainer.addView(linkPreviewDraftView)
        requestLayout()
    }

    fun updateLinkPreviewDraft(glide: GlideRequests, updatedLinkPreview: LinkPreview) {
        // Update our `linkPreview` property with the new (provided as an argument to this function)
        // then update the View from that.
        linkPreview = updatedLinkPreview.also { linkPreviewDraftView?.update(glide, it) }
    }

    override fun cancelLinkPreviewDraft() {
        binding.inputBarAdditionalContentContainer.removeView(linkPreviewDraftView)
        linkPreview = null
        linkPreviewDraftView = null
        requestLayout()
    }

    private fun showOrHideInputIfNeeded() {
        if (showInput) {
            setOf( binding.inputBarEditText, attachmentsButton ).forEach { it.isVisible = true }
            microphoneButton.isVisible = text.isEmpty()
            sendButton.isVisible = text.isNotEmpty()
        } else {
            cancelQuoteDraft()
            cancelLinkPreviewDraft()
            val views = setOf( binding.inputBarEditText, attachmentsButton, microphoneButton, sendButton )
            views.forEach { it.isVisible = false }
        }
    }

    private fun showOrHideMediaControlsIfNeeded() {
        setOf(attachmentsButton, microphoneButton).forEach { it.snIsEnabled = showMediaControls }
    }

    fun addTextChangedListener(listener: (String) -> Unit) {
        binding.inputBarEditText.addTextChangedListener(listener)
    }

    fun setInputBarEditableFactory(factory: Editable.Factory) {
        binding.inputBarEditText.setEditableFactory(factory)
    }
}

interface InputBarDelegate {
    fun inputBarEditTextContentChanged(newContent: CharSequence)
    fun toggleAttachmentOptions()
    fun showVoiceMessageUI()
    fun startRecordingVoiceMessage()
    fun onMicrophoneButtonMove(event: MotionEvent)
    fun onMicrophoneButtonCancel(event: MotionEvent)
    fun onMicrophoneButtonUp(event: MotionEvent)
    fun sendMessage()
    fun commitInputContent(contentUri: Uri)
}
