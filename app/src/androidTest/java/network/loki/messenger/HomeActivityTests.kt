package network.loki.messenger

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import network.loki.messenger.util.sendMessage
import network.loki.messenger.util.setupLoggedInState
import network.loki.messenger.util.waitFor
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.home.HomeActivity

@RunWith(AndroidJUnit4::class)
@SmallTest
class HomeActivityTests {

    @get:Rule
    var activityRule = ActivityScenarioRule(HomeActivity::class.java)

    private val activityMonitor = Instrumentation.ActivityMonitor(ConversationActivityV2::class.java.name, null, false)

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().addMonitor(activityMonitor)
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().removeMonitor(activityMonitor)
    }

    private fun goToMyChat() {
        onView(withId(R.id.newConversationButton)).perform(ViewActions.click())
        onView(withId(R.id.createPrivateChatButton)).perform(ViewActions.click())
        // new chat
        Thread.sleep(500)
        onView(withId(R.id.publicKeyEditText)).perform(ViewActions.closeSoftKeyboard())
        onView(withId(R.id.copyButton)).perform(ViewActions.click())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var copied: String
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            copied = clipboardManager.primaryClip!!.getItemAt(0).text.toString()
        }
        onView(withId(R.id.publicKeyEditText)).perform(ViewActions.typeText(copied))
        onView(withId(R.id.publicKeyEditText)).perform(ViewActions.closeSoftKeyboard())
        onView(withId(R.id.createPrivateChatButton)).perform(ViewActions.click())
    }

    @Test
    fun testLaunches_dismiss_seedView() {
        setupLoggedInState()
        onView(allOf(withId(R.id.button), isDescendantOfA(withId(R.id.seedReminderView)))).perform(ViewActions.click())
        onView(withId(R.id.copyButton)).perform(ViewActions.click())
        pressBack()
        onView(withId(R.id.seedReminderView)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testIsVisible_seedView() {
        setupLoggedInState()
        onView(withId(R.id.seedReminderView)).check(matches(isCompletelyDisplayed()))
    }

    @Test
    fun testIsVisible_alreadyDismissed_seedView() {
        setupLoggedInState(hasViewedSeed = true)
        onView(withId(R.id.seedReminderView)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testChat_withSelf() {
        setupLoggedInState()
        goToMyChat()
        TextSecurePreferences.setLinkPreviewsEnabled(InstrumentationRegistry.getInstrumentation().targetContext, true)
        with (activityMonitor.waitForActivity() as ConversationActivityV2) {
            sendMessage("howdy")
            sendMessage("test")
            // tests url rewriter doesn't crash
            sendMessage("https://www.getsession.org?random_query_parameter=testtesttesttesttesttesttesttest&other_query_parameter=testtesttesttesttesttesttesttest")
            sendMessage("https://www.ámazon.com")
        }
    }

    @Test
    fun testChat_displaysCorrectUrl() {
        setupLoggedInState()
        goToMyChat()
        TextSecurePreferences.setLinkPreviewsEnabled(InstrumentationRegistry.getInstrumentation().targetContext, true)
        // given the link url text
        val url = "https://www.ámazon.com"
        with (activityMonitor.waitForActivity() as ConversationActivityV2) {
            sendMessage(url, LinkPreview(url, "amazon", Optional.absent()))
        }

        // when the URL span is clicked
        onView(withSubstring(url)).perform(ViewActions.click())

        // then the URL dialog should be displayed with a known punycode url
        val amazonPuny = "https://www.xn--mazon-wqa.com/"

        val dialogPromptText = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dialog_open_url_explanation, amazonPuny)

        onView(isRoot()).perform(waitFor(1000)) // no other way for this to work apparently
        onView(withText(dialogPromptText)).check(matches(isDisplayed()))
    }

}