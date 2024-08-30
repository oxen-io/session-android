package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.tabs.TabLayoutMediator
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewConversationActionBarBinding
import network.loki.messenger.databinding.ViewConversationSettingBinding
import network.loki.messenger.libsession_util.util.ExpiryMode.AfterRead
import org.session.libsession.LocalisedTimeUtil
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.StringSubstitutionConstants.DISAPPEARING_MESSAGES_TYPE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_LARGE_KEY
import org.session.libsession.utilities.modifyLayoutParams
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.ui.getSubbedString

@AndroidEntryPoint
class ConversationActionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val binding = ViewConversationActionBarBinding.inflate(LayoutInflater.from(context), this, true)

    @Inject lateinit var lokiApiDb: LokiAPIDatabase
    @Inject lateinit var groupDb: GroupDatabase

    var delegate: ConversationActionBarDelegate? = null

    private val settingsAdapter = ConversationSettingsAdapter { setting ->
        if (setting.settingType == ConversationSettingType.EXPIRATION) {
            delegate?.onDisappearingMessagesClicked()
        }
    }

    init {
        var previousState: Int
        var currentState = 0
        binding.settingsPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                val currentPage: Int = binding.settingsPager.currentItem
                val lastPage = maxOf( (binding.settingsPager.adapter?.itemCount ?: 0) - 1, 0)
                if (currentPage == lastPage || currentPage == 0) {
                    previousState = currentState
                    currentState = state
                    if (previousState == 1 && currentState == 0) {
                        binding.settingsPager.setCurrentItem(if (currentPage == 0) lastPage else 0, true)
                    }
                }
            }
        })
        binding.settingsPager.adapter = settingsAdapter
        TabLayoutMediator(binding.settingsTabLayout, binding.settingsPager) { _, _ -> }.attach()
    }

    fun bind(
        delegate: ConversationActionBarDelegate,
        threadId: Long,
        recipient: Recipient,
        config: ExpirationConfiguration? = null,
        openGroup: OpenGroup? = null
    ) {
        this.delegate = delegate
        binding.profilePictureView.layoutParams = resources.getDimensionPixelSize(
            if (recipient.isClosedGroupRecipient) R.dimen.medium_profile_picture_size else R.dimen.small_profile_picture_size
        ).let { LayoutParams(it, it) }
        update(recipient, openGroup, config)
    }

    fun update(recipient: Recipient, openGroup: OpenGroup? = null, config: ExpirationConfiguration? = null) {
        binding.profilePictureView.update(recipient)
        binding.conversationTitleView.text = recipient.takeUnless { it.isLocalNumber }?.toShortString() ?: context.getString(R.string.noteToSelf)
        updateSubtitle(recipient, openGroup, config)

        binding.conversationTitleContainer.modifyLayoutParams<MarginLayoutParams> {
            marginEnd = if (recipient.showCallMenu()) 0 else binding.profilePictureView.width
        }
    }

    fun updateSubtitle(recipient: Recipient, openGroup: OpenGroup? = null, config: ExpirationConfiguration? = null) {
        val settings = mutableListOf<ConversationSetting>()

        // Specify the disappearing messages subtitle if we should
        if (config?.isEnabled == true) {
            // Get the type of disappearing message and the abbreviated duration..
            val dmTypeString = when (config.expiryMode) {
                is AfterRead -> context.getString(R.string.read)
                else -> context.getString(R.string.send)
            }
            val durationAbbreviated = ExpirationUtil.getExpirationAbbreviatedDisplayValue(config.expiryMode.expirySeconds)

            // ..then substitute into the string..
            val subtitleTxt = context.getSubbedString(R.string.disappearingMessagesDisappear,
                DISAPPEARING_MESSAGES_TYPE_KEY to dmTypeString,
                TIME_KEY to durationAbbreviated
                )

            // .. and apply to the subtitle.
            settings += ConversationSetting(
                subtitleTxt,
                ConversationSettingType.EXPIRATION,
                R.drawable.ic_timer,
                resources.getString(R.string.AccessibilityId_disappearingMessagesDisappear)
            )
        }

        if (recipient.isMuted) {
            settings += ConversationSetting(
                recipient.mutedUntil.takeUnless { it == Long.MAX_VALUE }
                    ?.let {
                        val mutedDuration = (it - System.currentTimeMillis()).milliseconds
                        val durationString = LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(context, mutedDuration)
                        context.getSubbedString(R.string.notificationsMuteFor, TIME_LARGE_KEY to durationString)
                    }
                    ?: context.getString(R.string.notificationsMuted),
                ConversationSettingType.NOTIFICATION,
                R.drawable.ic_outline_notifications_off_24
            )
        }

        if (recipient.isGroupRecipient) {
            val title = if (recipient.isCommunityRecipient) {
                val userCount = openGroup?.let { lokiApiDb.getUserCount(it.room, it.server) } ?: 0
                resources.getQuantityString(R.plurals.membersActive, userCount, userCount)
            } else {
                val userCount = groupDb.getGroupMemberAddresses(recipient.address.toGroupString(), true).size
                resources.getQuantityString(R.plurals.members, userCount, userCount)
            }
            settings += ConversationSetting(title, ConversationSettingType.MEMBER_COUNT)
        }

        settingsAdapter.submitList(settings)
        binding.settingsTabLayout.isVisible = settings.size > 1
    }

    class ConversationSettingsAdapter(
        private val settingsListener: (ConversationSetting) -> Unit
    ) : ListAdapter<ConversationSetting, ConversationSettingsAdapter.SettingViewHolder>(SettingsDiffer()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            return SettingViewHolder(ViewConversationSettingBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
            holder.bind(getItem(position), itemCount) {
                settingsListener.invoke(it)
            }
        }

        class SettingViewHolder(
            private val binding: ViewConversationSettingBinding
        ): RecyclerView.ViewHolder(binding.root) {

            fun bind(setting: ConversationSetting, itemCount: Int, listener: (ConversationSetting) -> Unit) {
                binding.root.setOnClickListener { listener.invoke(setting) }
                binding.root.contentDescription = setting.contentDescription
                binding.iconImageView.setImageResource(setting.iconResId)
                binding.iconImageView.isVisible = setting.iconResId > 0
                binding.titleView.text = setting.title
                binding.leftArrowImageView.isVisible = itemCount > 1
                binding.rightArrowImageView.isVisible = itemCount > 1
            }
        }

        class SettingsDiffer: DiffUtil.ItemCallback<ConversationSetting>() {
            override fun areItemsTheSame(oldItem: ConversationSetting, newItem: ConversationSetting): Boolean = oldItem.settingType === newItem.settingType
            override fun areContentsTheSame(oldItem: ConversationSetting, newItem: ConversationSetting): Boolean = oldItem == newItem
        }
    }
}

fun interface ConversationActionBarDelegate {
    fun onDisappearingMessagesClicked()
}

data class ConversationSetting(
    val title: String,
    val settingType: ConversationSettingType,
    val iconResId: Int = 0,
    val contentDescription: String = ""
)

enum class ConversationSettingType {
    EXPIRATION,
    MEMBER_COUNT,
    NOTIFICATION
}
