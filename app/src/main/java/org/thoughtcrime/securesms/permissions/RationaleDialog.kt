package org.thoughtcrime.securesms.permissions

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import network.loki.messenger.R
import org.session.libsession.utilities.ViewUtil
import org.thoughtcrime.securesms.showSessionDialog

object RationaleDialog {
    @JvmStatic
    fun show(
        context: Context,
        message: String,
        onPositive: Runnable,
        onNegative: Runnable,
        @DrawableRes vararg drawables: Int
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.permissions_rationale_dialog, null)
            .apply { clipToOutline = true }
        val header = view.findViewById<ViewGroup>(R.id.header_container)
        view.findViewById<TextView>(R.id.message).text = message

        fun addIcon(id: Int) {
            ImageView(context).apply {
                setImageDrawable(ResourcesCompat.getDrawable(context.resources, id, context.theme))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            }.also(header::addView)
        }

        fun addPlus() {
            TextView(context).apply {
                text = "+"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    ViewUtil.dpToPx(context, 20).let { setMargins(it, 0, it, 0) }
                }
            }.also(header::addView)
        }

        drawables.firstOrNull()?.let(::addIcon)
        drawables.drop(1).forEach { addPlus(); addIcon(it) }

        return context.showSessionDialog {
            view(view)
            button(R.string.Permissions_continue) { onPositive.run() }
            button(R.string.Permissions_not_now) { onNegative.run() }
        }
    }
}
