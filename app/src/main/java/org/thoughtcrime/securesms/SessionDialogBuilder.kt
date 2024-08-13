package org.thoughtcrime.securesms

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.updateMargins
import androidx.fragment.app.Fragment
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.URL_KEY
import org.thoughtcrime.securesms.conversation.v2.Util.writeTextToClipboard
import org.thoughtcrime.securesms.util.toPx

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class DialogDsl

@DialogDsl
class SessionDialogBuilder(val context: Context) {

    val dp20 = toPx(20, context.resources)
    val dp40 = toPx(40, context.resources)
    val dp60 = toPx(60, context.resources)

    private val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(context)

    private var dialog: AlertDialog? = null
    fun dismiss() = dialog?.dismiss()

    private val topView = LinearLayout(context)
        .apply { setPadding(0, dp20, 0, 0) }
        .apply { orientation = VERTICAL }
        .also(dialogBuilder::setCustomTitle)

    val contentView = LinearLayout(context).apply { orientation = VERTICAL }

    private val buttonLayout = LinearLayout(context)

    private val root = LinearLayout(context).apply { orientation = VERTICAL }
        .also(dialogBuilder::setView)
        .apply {
            addView(contentView)
            addView(buttonLayout)
        }

    // Main title entry point
    fun title(text: String?) {
        text(text, R.style.TextAppearance_AppCompat_Title) { setPadding(dp20, 0, dp20, 0) }
    }

    // Convenience assessor for title that takes a string resource
    fun title(@StringRes id: Int) = title(context.getString(id))

    // Convenience accessor for title that takes a CharSequence
    fun title(text: CharSequence?) = title(text?.toString())

    fun text(@StringRes id: Int, style: Int = 0) = text(context.getString(id), style)

    fun text(text: CharSequence?, @StyleRes style: Int = 0) {
        text(text, style) {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { updateMargins(dp40, 0, dp40, 0) }
        }
    }

    private fun text(text: CharSequence?, @StyleRes style: Int, modify: TextView.() -> Unit) {
        text ?: return
        TextView(context, null, 0, style)
            .apply {
                setText(text)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                modify()
            }.let(topView::addView)

        Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp20)
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
        onSelect: (Int) -> Unit
    ) = singleChoiceItems(options.toTypedArray(), currentSelected, onSelect)

    fun singleChoiceItems(
        options: Array<String>,
        currentSelected: Int = 0,
        onSelect: (Int) -> Unit
    ): AlertDialog.Builder = dialogBuilder.setSingleChoiceItems(
        options,
        currentSelected
    ) { dialog, it -> onSelect(it); dialog.dismiss() }

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

    fun copyUrlButton(listener: (() -> Unit) = {}) = button(android.R.string.copyUrl, R.string.AccessibilityId_copy) { listener() }

    fun button(
        @StringRes text: Int,
        @StringRes contentDescriptionRes: Int = text,
        @StyleRes style: Int = R.style.Widget_Session_Button_Dialog_UnimportantText,
        dismiss: Boolean = true,
        listener: (() -> Unit) = {}
    ) = Button(context, null, 0, style).apply {
            setText(text)
            contentDescription = resources.getString(contentDescriptionRes)
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

// Method to show a dialog used to open or copy a URL
fun Context.showOpenUrlDialog(url: String, showCloseButton: Boolean = true): AlertDialog {
    return SessionDialogBuilder(this).apply {
        // If we're not showing a close button we can just use a simple title..
        if (!showCloseButton) {
            title(R.string.urlOpen)
        } else {
            // ..otherwise we have to jump through some hoops to add a close button.

            // Create a RelativeLayout as the container for the custom title
            val titleLayout = RelativeLayout(context).apply {
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Create a TextView for the title
            val titleTextView = TextView(context).apply {
                // Set the text and display it in the correct 'title' style
                text = context.getString(R.string.urlOpen)
                setTextAppearance(R.style.TextAppearance_AppCompat_Title)

                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.CENTER_HORIZONTAL)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                }
            }

            // Create an ImageButton for the close button
            val closeButton = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Use a standard Android close icon
                background = null // Remove the background to make it look like an icon
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END) // Place the close button on the "right" side
                    addRule(RelativeLayout.CENTER_VERTICAL)
                }
                contentDescription = context.getString(R.string.close)
            }

            // // Close the dialog when the button is clicked
            closeButton.setOnClickListener { dismiss() }

            // Add the TextView and ImageButton to the RelativeLayout..
            titleLayout.addView(titleTextView)
            titleLayout.addView(closeButton)

            // ..and then add that layout to the contentView.
            contentView.addView(titleLayout)
        }

        // Create a TextView for the "Are you sure you want to open this URL?"
        val txtView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { updateMargins(dp40, 0, dp40, 0) }

            // Substitute the URL into the string then make it bold
            val txt = Phrase.from(context, R.string.urlOpenDescription).put(URL_KEY, url).format().toString()
            val txtWithBoldedURL = SpannableString(txt)
            val urlStart = txt.indexOf(url)
            if (urlStart >= 0) {
                txtWithBoldedURL.setSpan(
                    StyleSpan(Typeface.BOLD),
                    urlStart,
                    urlStart + url.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            text = txtWithBoldedURL

            gravity = Gravity.CENTER // Center the text
        }

        // Create a ScrollView and add the TextView to it
        val scrollView = ScrollView(context).apply {
            addView(txtView)

            // Apply padding to the ScrollView so that the scroll bar isn't right up against the edge.
            // We'll apply the same padding to both sides to keep the text centered.
            setPadding(dp20, 0, dp20, 0)

            // Place the scroll bar inside the container.
            // See the following for how different options look: https://stackoverflow.com/questions/3103132/android-listview-scrollbarstyle
            scrollBarStyle = ScrollView.SCROLLBARS_INSIDE_INSET
        }

        // If the textView takes up 5 lines or more then show the scroll bar, force it to remain visible,
        // and set the ScrollView height accordingly.
        txtView.viewTreeObserver.addOnGlobalLayoutListener {
            // Only display the vertical scroll bar if the text takes up 5 lines or more
            val maxLines = 5
            if (txtView.lineCount >= maxLines) {
                scrollView.isVerticalScrollBarEnabled = true
                scrollView.setScrollbarFadingEnabled(false)
                scrollView.isVerticalFadingEdgeEnabled = false
                val lineHeight = txtView.lineHeight
                scrollView.layoutParams.height = lineHeight * maxLines
            }
        }

        // Add the ScrollView to the contentView and then add the 'Open' and 'Copy URL' buttons.
        // Note: The text and contentDescription are set on the `copyUrlButton` by the function.
        contentView.addView(scrollView)
        dangerButton(R.string.open, R.string.AccessibilityId_urlOpenBrowser) { openUrl(url) }
        copyUrlButton { writeTextToClipboard(context, url) }
    }.show()
}

// Method to actually open a given URL via an Intent that will use the default browser
fun Context.openUrl(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).let(::startActivity)

fun Fragment.showSessionDialog(build: SessionDialogBuilder.() -> Unit): AlertDialog =
    SessionDialogBuilder(requireContext()).apply { build() }.show()

fun Fragment.createSessionDialog(build: SessionDialogBuilder.() -> Unit): AlertDialog =
    SessionDialogBuilder(requireContext()).apply { build() }.create()
