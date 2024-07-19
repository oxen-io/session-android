package org.thoughtcrime.securesms.components.emoji

import android.net.Uri
import androidx.annotation.AttrRes
import java.util.LinkedList

class CompositeEmojiPageModel(
    @field:AttrRes @param:AttrRes private val iconAttr: Int,
    private val models: List<EmojiPageModel>
) : EmojiPageModel {

    override fun getKey(): String {
        return if (models.isEmpty()) "" else models[0].key
    }

    override fun getIconAttr(): Int { return iconAttr }

    override fun getEmoji(): List<String> {
        val emojis: MutableList<String> = LinkedList()
        for (model in models) {
            emojis.addAll(model.emoji)
        }
        return emojis
    }

    override fun getDisplayEmoji(): List<Emoji> {
        val emojis: MutableList<Emoji> = LinkedList()
        for (model in models) {
            emojis.addAll(model.displayEmoji)
        }
        return emojis
    }

    override fun hasSpriteMap(): Boolean { return false }

    override fun getSpriteUri(): Uri? { return null }

    override fun isDynamic(): Boolean { return false }
}
