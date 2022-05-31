package org.thoughtcrime.securesms.keyboard.emoji.search

import android.net.Uri
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.concurrent.SignalExecutors
import org.thoughtcrime.securesms.components.emoji.Emoji
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.emoji.EmojiSource
import java.util.function.Consumer

private const val MINIMUM_QUERY_THRESHOLD = 1
private const val EMOJI_SEARCH_LIMIT = 20

class EmojiSearchRepository() {

  fun submitQuery(query: String, includeRecents: Boolean, limit: Int = EMOJI_SEARCH_LIMIT, consumer: Consumer<EmojiPageModel>) {
    if (query.length < MINIMUM_QUERY_THRESHOLD && includeRecents) {
      consumer.accept(RecentEmojiPageModel(MessagingModuleConfiguration.shared.context))
    } else {
      SignalExecutors.SERIAL.execute {
        val emoji: List<String> = emptyList() //TODO: query

        val displayEmoji: List<Emoji> = emoji
          .mapNotNull { canonical -> EmojiSource.latest.canonicalToVariations[canonical] }
          .map { Emoji(it) }

        consumer.accept(EmojiSearchResultsPageModel(emoji, displayEmoji))
      }
    }
  }

  private class EmojiSearchResultsPageModel(
    private val emoji: List<String>,
    private val displayEmoji: List<Emoji>
  ) : EmojiPageModel {
    override fun getKey(): String = ""

    override fun getIconAttr(): Int = -1

    override fun getEmoji(): List<String> = emoji

    override fun getDisplayEmoji(): List<Emoji> = displayEmoji

    override fun hasSpriteMap(): Boolean = false

    override fun getSpriteUri(): Uri? = null

    override fun isDynamic(): Boolean = false
  }
}
