package org.thoughtcrime.securesms.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_LIGHT
import org.thoughtcrime.securesms.BaseActionBarActivity

fun BaseActionBarActivity.setUpActionBarSessionLogo(hideBackButton: Boolean = false) {
    val actionbar = supportActionBar!!

    actionbar.setDisplayShowHomeEnabled(false)
    actionbar.setDisplayShowTitleEnabled(false)
    actionbar.setDisplayHomeAsUpEnabled(false)
    actionbar.setHomeButtonEnabled(false)

    actionbar.setCustomView(R.layout.session_logo_action_bar_content)
    actionbar.setDisplayShowCustomEnabled(true)

    val rootView: Toolbar = actionbar.customView!!.parent as Toolbar
    rootView.setPadding(0,0,0,0)
    rootView.setContentInsetsAbsolute(0,0);

    val backButton = actionbar.customView!!.findViewById<View>(R.id.back_button)
    if (hideBackButton) {
        backButton.visibility = View.GONE
    } else {
        backButton.visibility = View.VISIBLE
        backButton.setOnClickListener {
            onSupportNavigateUp()
        }
    }
}

val AppCompatActivity.defaultSessionRequestCode: Int
    get() = 42

fun AppCompatActivity.push(intent: Intent, isForResult: Boolean = false) {
    if (isForResult) {
        startActivityForResult(intent, defaultSessionRequestCode)
    } else {
        startActivity(intent)
    }
    overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
}

fun AppCompatActivity.show(intent: Intent, isForResult: Boolean = false) {
    if (isForResult) {
        startActivityForResult(intent, defaultSessionRequestCode)
    } else {
        startActivity(intent)
    }
    overridePendingTransition(R.anim.slide_from_bottom, R.anim.fade_scale_out)
}

interface ActivityDispatcher {
    companion object {
        const val SERVICE = "ActivityDispatcher_SERVICE"
        @SuppressLint("WrongConstant")
        fun get(context: Context) = context.getSystemService(SERVICE) as? ActivityDispatcher
    }
    fun dispatchIntent(body: (Context)->Intent?)
    fun showDialog(dialogFragment: DialogFragment, tag: String? = null)
}

fun TextSecurePreferences.themeState(): ThemeState {
    val themeStyle = getThemeStyle().getThemeStyle()
    val accentStyle = getAccentColorStyle() ?: themeStyle.getDefaultAccentColor()
    val followSystem = getFollowSystemSettings()
    return ThemeState(
        themeStyle,
        accentStyle,
        followSystem
    )
}

@StyleRes
fun String.getThemeStyle(): Int = when (this) {
    CLASSIC_DARK -> R.style.Classic_Dark
    CLASSIC_LIGHT -> R.style.Classic_Light
    OCEAN_DARK -> R.style.Ocean_Dark
    OCEAN_LIGHT -> R.style.Ocean_Light
    else -> throw NullPointerException("The style [$this] is not supported")
}

@StyleRes
fun Int.getDefaultAccentColor(): Int = when (this) {
    R.style.Ocean_Dark, R.style.Ocean_Light -> R.style.PrimaryBlue
    else -> R.style.PrimaryGreen
}

data class ThemeState (
    @StyleRes val theme: Int,
    @StyleRes val accentStyle: Int,
    val followSystem: Boolean
)

inline fun <reified T: Activity> Activity.show() = Intent(this, T::class.java).also(::startActivity).let { overridePendingTransition(R.anim.slide_from_bottom, R.anim.fade_scale_out) }
inline fun <reified T: Activity> Activity.push(modify: Intent.() -> Unit = {}) = Intent(this, T::class.java).also(modify).also(::startActivity).let { overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left) }
inline fun <reified T: Activity> Context.start(modify: Intent.() -> Unit = {}) = Intent(this, T::class.java).also(modify).apply { addFlags(FLAG_ACTIVITY_SINGLE_TOP) }.let(::startActivity)
