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
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewConversationActionBarBinding
import network.loki.messenger.databinding.ViewConversationSettingBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.modifyLayoutParams
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionManagerUtilities
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import javax.inject.Inject

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
        MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded(threadId, context)
        update(recipient, openGroup, config)
    }

    fun update(recipient: Recipient, openGroup: OpenGroup? = null, config: ExpirationConfiguration? = null) {
        binding.profilePictureView.update(recipient)
        binding.conversationTitleView.text = recipient.takeUnless { it.isLocalNumber }?.toShortString() ?: context.getString(R.string.note_to_self)
        updateSubtitle(recipient, openGroup, config)

        binding.conversationTitleContainer.modifyLayoutParams<MarginLayoutParams> {
            marginEnd = if (recipient.showCallMenu()) 0 else binding.profilePictureView.width
        }
    }

    fun updateSubtitle(recipient: Recipient, openGroup: OpenGroup? = null, config: ExpirationConfiguration? = null) {
        val settings = mutableListOf<ConversationSetting>()
        if (config?.isEnabled == true) {
            val prefix = when (config.expiryMode) {
                is ExpiryMode.AfterRead -> R.string.expiration_type_disappear_after_read
                else -> R.string.expiration_type_disappear_after_send
            }.let(context::getString)
            settings += ConversationSetting(
                "$prefix - ${ExpirationUtil.getExpirationAbbreviatedDisplayValue(context, config.expiryMode.expirySeconds)}",
                ConversationSettingType.EXPIRATION,
                R.drawable.ic_timer,
                resources.getString(R.string.AccessibilityId_disappearing_messages_type_and_time)
            )
        }
        if (recipient.isMuted) {
            settings += ConversationSetting(
                recipient.mutedUntil.takeUnless { it == Long.MAX_VALUE }
                    ?.let { context.getString(R.string.ConversationActivity_muted_until_date, DateUtils.getFormattedDateTime(it, "EEE, MMM d, yyyy HH:mm", Locale.getDefault())) }
                    ?: context.getString(R.string.ConversationActivity_muted_forever),
                ConversationSettingType.NOTIFICATION,
                R.drawable.ic_outline_notifications_off_24
            )
        }
        if (recipient.isGroupRecipient) {
            val title = if (recipient.isOpenGroupRecipient) {
                val userCount = openGroup?.let { lokiApiDb.getUserCount(it.room, it.server) } ?: 0
                context.getString(R.string.ConversationActivity_active_member_count, userCount)
            } else {
                val userCount = groupDb.getGroupMemberAddresses(recipient.address.toGroupString(), true).size
                context.getString(R.string.ConversationActivity_member_count, userCount)
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
