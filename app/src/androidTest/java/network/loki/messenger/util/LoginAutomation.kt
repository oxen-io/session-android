package network.loki.messenger.util

import android.Manifest
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.adevinta.android.barista.interaction.PermissionGranter
import network.loki.messenger.R
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBar
import org.thoughtcrime.securesms.mms.GlideApp

fun setupLoggedInState(hasViewedSeed: Boolean = false) {
    // landing activity
    onView(ViewMatchers.withId(R.id.registerButton)).perform(ViewActions.click())
    // session ID - register activity
    onView(ViewMatchers.withId(R.id.registerButton)).perform(ViewActions.click())
    // display name selection
    onView(ViewMatchers.withId(R.id.displayNameEditText))
        .perform(ViewActions.typeText("test-user123"))
    onView(ViewMatchers.withId(R.id.registerButton)).perform(ViewActions.click())
    // PN select
    if (hasViewedSeed) {
        // has viewed seed is set to false after register activity
        TextSecurePreferences.setHasViewedSeed(
            InstrumentationRegistry.getInstrumentation().targetContext,
            true
        )
    }
    onView(ViewMatchers.withId(R.id.backgroundPollingOptionView))
        .perform(ViewActions.click())
    onView(ViewMatchers.withId(R.id.registerButton)).perform(ViewActions.click())
    // allow notification permission
    PermissionGranter.allowPermissionsIfNeeded(Manifest.permission.POST_NOTIFICATIONS)
}

fun ConversationActivityV2.sendMessage(messageToSend: String, linkPreview: LinkPreview? = null) {
    // assume in chat activity
    onView(
        Matchers.allOf(
            ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.inputBar)),
            ViewMatchers.withId(R.id.inputBarEditText)
        )
    ).perform(ViewActions.replaceText(messageToSend))
    if (linkPreview != null) {
        val glide = GlideApp.with(this)
        this.findViewById<InputBar>(R.id.inputBar).updateLinkPreviewDraft(glide, linkPreview)
    }
    onView(
        Matchers.allOf(
            ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.inputBar)),
            InputBarButtonDrawableMatcher.inputButtonWithDrawable(R.drawable.ic_arrow_up)
        )
    ).perform(ViewActions.click())
    // TODO: text can flaky on cursor reload, figure out a better way to wait for the UI to settle with new data
    onView(ViewMatchers.isRoot()).perform(waitFor(500))
}

/**
 * Perform action of waiting for a specific time.
 */
fun waitFor(millis: Long): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View>? {
            return ViewMatchers.isRoot()
        }

        override fun getDescription(): String = "Wait for $millis milliseconds."

        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadForAtLeast(millis)
        }
    }
}