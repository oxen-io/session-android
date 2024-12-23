package org.thoughtcrime.securesms.components.menu

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt


/**
 * Represents an action to be rendered
 */
data class ActionItem(
  @AttrRes val iconRes: Int,
  val title: Int,
  val action: Runnable,
  val contentDescription: Int? = null,
  val subtitle: ((Context) -> CharSequence?)? = null,
  @ColorInt val color: Int? = null,
)
