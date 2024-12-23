package org.thoughtcrime.securesms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.Space
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.updateMargins
import androidx.fragment.app.Fragment
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.toPx

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class DialogDsl

@DialogDsl
class SessionDialogBuilder(val context: Context) {

    private val dp8 = toPx(8, context.resources)
    private val dp20 = toPx(20, context.resources)
    private val dp40 = toPx(40, context.resources)
    private val dp60 = toPx(60, context.resources)

    private val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(context)

    private var dialog: AlertDialog? = null
    fun dismiss() = dialog?.dismiss()

    private val topView = LinearLayout(context)
        .apply { setPadding(0, dp20, 0, 0) }
        .apply { orientation = VERTICAL }
        .also(dialogBuilder::setCustomTitle)

    private val contentView = LinearLayout(context).apply { orientation = VERTICAL }

    private val buttonLayout = LinearLayout(context)

    private val root = LinearLayout(context).apply { orientation = VERTICAL }
        .also(dialogBuilder::setView)
        .apply {
            addView(contentView)
            addView(buttonLayout)
        }

    // Main title entry point
    fun title(text: String?) {
        text(
            text = text,
            qaTag = context.getString(R.string.AccessibilityId_modalTitle),
            style = R.style.TextAppearance_Session_Dialog_Title) { setPadding(dp20, 0, dp20, 0) }
    }

    // Convenience assessor for title that takes a string resource
    fun title(@StringRes id: Int) = title(context.getString(id))

    // Convenience accessor for title that takes a CharSequence
    fun title(text: CharSequence?) = title(text?.toString())

    fun text(@StringRes id: Int, style: Int? = null) = text(context.getString(id), style)

    fun text(text: CharSequence?, @StyleRes style: Int? = null) {
        text(text = text, style = style) {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { updateMargins(dp40, 0, dp40, 0) }
        }
    }

    private fun text(
        text: CharSequence?,
        qaTag: String = context.getString(R.string.AccessibilityId_modalMessage),
        @StyleRes style: Int? = null,
        modify: TextView.() -> Unit
    ) {
        text ?: return
        TextView(context, null, 0, style ?: R.style.TextAppearance_Session_Dialog_Message)
            .apply {
                setText(text)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                contentDescription = qaTag
                modify()
            }.let(topView::addView)

        Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp8)
        }.let(topView::addView)
    }

    fun htmlText(@StringRes id: Int, @StyleRes style: Int = 0, modify: TextView.() -> Unit = {}) {
        text(HtmlCompat.fromHtml(context.resources.getString(id), 0))
    }

    fun view(view: View) = contentView.addView(view)

    fun view(@LayoutRes layout: Int): View = LayoutInflater.from(context).inflate(layout, contentView)

    fun iconAttribute(@AttrRes icon: Int): AlertDialog.Builder = dialogBuilder.setIconAttribute(icon)

    fun singleChoiceItems(
        options: Collection<String>,
        currentSelected: Int = 0,
        dismissOnRadioSelect: Boolean = true,
        onSelect: (Int) -> Unit
    ) = singleChoiceItems(
        options.toTypedArray(),
        currentSelected,
        dismissOnRadioSelect,
        onSelect
    )

    fun singleChoiceItems(
        options: Array<String>,
        currentSelected: Int = 0,
        dismissOnRadioSelect: Boolean = true,
        onSelect: (Int) -> Unit
    ): AlertDialog.Builder{
        val adapter = ArrayAdapter<CharSequence>(context, R.layout.view_dialog_single_choice_item, options)

        return dialogBuilder.setSingleChoiceItems(
            adapter,
            currentSelected
        ) { dialog, it ->
            onSelect(it)
            if(dismissOnRadioSelect) dialog.dismiss()
        }
    }

    fun items(
        options: Array<String>,
        onSelect: (Int) -> Unit
    ): AlertDialog.Builder = dialogBuilder.setItems(
        options,
    ) { dialog, it -> onSelect(it); dialog.dismiss() }

    fun dangerButton(
        @StringRes text: Int,
        @StringRes contentDescription: Int = text,
        listener: () -> Unit = {}
    ) = button(
        text,
        contentDescription,
        R.style.Widget_Session_Button_Dialog_DangerText,
    ) { listener() }

    fun okButton(listener: (() -> Unit) = {}) = button(android.R.string.ok) { listener() }

    fun cancelButton(listener: (() -> Unit) = {}) = button(android.R.string.cancel, R.string.AccessibilityId_cancel) { listener() }

    fun button(
        @StringRes text: Int,
        @StringRes contentDescriptionRes: Int = text,
        @StyleRes style: Int = R.style.Widget_Session_Button_Dialog_UnimportantText,
        @ColorRes textColor: Int? = null,
        dismiss: Boolean = true,
        listener: (() -> Unit) = {}
    ) = Button(context, null, 0, style).apply {
            setText(text)
            textColor?.let{
                setTextColor(it)
            }
            contentDescription = resources.getString(text) // QA now wants the qa tag to mtch the button's text
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp60, 1f)
            setOnClickListener {
                listener.invoke()
                if (dismiss) dismiss()
            }
        }.let(buttonLayout::addView)

    fun create(): AlertDialog = dialogBuilder.create().also { dialog = it }
    fun show(): AlertDialog = dialogBuilder.show().also { dialog = it }
}

fun Context.showSessionDialog(build: SessionDialogBuilder.() -> Unit): AlertDialog =
    SessionDialogBuilder(this).apply { build() }.show()

public fun Context.copyURLToClipboard(url: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(url, url)
    clipboard.setPrimaryClip(clip)
}

// Method to actually open a given URL via an Intent that will use the default browser
fun Context.openUrl(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).let(::startActivity)

fun Fragment.showSessionDialog(build: SessionDialogBuilder.() -> Unit): AlertDialog =
    SessionDialogBuilder(requireContext()).apply { build() }.show()

fun Fragment.createSessionDialog(build: SessionDialogBuilder.() -> Unit): AlertDialog =
    SessionDialogBuilder(requireContext()).apply { build() }.create()
