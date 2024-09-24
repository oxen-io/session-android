package org.thoughtcrime.securesms.emoji

import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import network.loki.messenger.R

/**
 * All the different Emoji categories the app is aware of in the order we want to display them.
 */
enum class EmojiCategory(val priority: Int, val key: String, @AttrRes val icon: Int) {
  PEOPLE(0, "People", R.attr.emoji_category_people),
  NATURE(1, "Nature", R.attr.emoji_category_nature),
  FOODS(2, "Foods", R.attr.emoji_category_foods),
  ACTIVITY(3, "Activity", R.attr.emoji_category_activity),
  PLACES(4, "Places", R.attr.emoji_category_places),
  OBJECTS(5, "Objects", R.attr.emoji_category_objects),
  SYMBOLS(6, "Symbols", R.attr.emoji_category_symbol),
  FLAGS(7, "Flags", R.attr.emoji_category_flags);

  @StringRes
  fun getCategoryLabel(): Int {
    return getCategoryLabel(icon)
  }

  companion object {
    @JvmStatic
    fun forKey(key: String) = values().first { it.key == key }

    @JvmStatic
    @StringRes
    fun getCategoryLabel(@AttrRes iconAttr: Int): Int {
      return when (iconAttr) {
        R.attr.emoji_category_people -> R.string.emojiCategorySmileys
        R.attr.emoji_category_nature -> R.string.emojiCategoryAnimals
        R.attr.emoji_category_foods -> R.string.emojiCategoryFood
        R.attr.emoji_category_activity -> R.string.emojiCategoryActivities
        R.attr.emoji_category_places -> R.string.emojiCategoryTravel
        R.attr.emoji_category_objects -> R.string.emojiCategoryObjects
        R.attr.emoji_category_symbol -> R.string.emojiCategorySymbols
        R.attr.emoji_category_flags -> R.string.emojiCategoryFlags
        else -> throw AssertionError()
      }
    }
  }
}
