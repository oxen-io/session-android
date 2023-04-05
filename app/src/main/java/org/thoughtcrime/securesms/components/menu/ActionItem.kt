package org.thoughtcrime.securesms.components.menu

import androidx.annotation.AttrRes

/**
 * Represents an action to be rendered
 */
data class ActionItem @JvmOverloads constructor(
  @AttrRes val iconRes: Int,
  val title: CharSequence,
  val action: Runnable,
  val contentDescription: String? = null
)
