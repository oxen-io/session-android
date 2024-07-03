package org.thoughtcrime.securesms.conversation.v2

import android.text.Editable
import android.text.Selection
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.thoughtcrime.securesms.conversation.v2.mention.MentionEditable
import org.thoughtcrime.securesms.conversation.v2.mention.MentionViewModel

@RunWith(RobolectricTestRunner::class)
class MentionEditableTest {
    private lateinit var mentionEditable: MentionEditable

    @Before
    fun setUp() {
        mentionEditable = MentionEditable()
    }

    @Test
    fun `should not have query when there is no 'at' symbol`() = runTest {
        mentionEditable.observeMentionSearchQuery().test {
            assertThat(awaitItem()).isNull()
            mentionEditable.simulateTyping("Some text")
            expectNoEvents()
        }
    }

    @Test
    fun `should have empty query after typing 'at' symbol`() = runTest {
        mentionEditable.observeMentionSearchQuery().test {
            assertThat(awaitItem()).isNull()

            mentionEditable.simulateTyping("Some text")
            expectNoEvents()

            mentionEditable.simulateTyping("@")
            assertThat(awaitItem())
                .isEqualTo(MentionEditable.SearchQuery(9, ""))
        }
    }

    @Test
    fun `should have some query after typing words following 'at' symbol`() = runTest {
        mentionEditable.observeMentionSearchQuery().test {
            assertThat(awaitItem()).isNull()

            mentionEditable.simulateTyping("Some text")
            expectNoEvents()

            mentionEditable.simulateTyping("@words")
            assertThat(awaitItem())
                .isEqualTo(MentionEditable.SearchQuery(9, "words"))
        }
    }

    @Test
    fun `should cancel query after a whitespace or another 'at' is typed`() = runTest {
        mentionEditable.observeMentionSearchQuery().test {
            assertThat(awaitItem()).isNull()

            mentionEditable.simulateTyping("@words")
            assertThat(awaitItem())
                .isEqualTo(MentionEditable.SearchQuery(0, "words"))

            mentionEditable.simulateTyping(" ")
            assertThat(awaitItem())
                .isNull()

            mentionEditable.simulateTyping("@query@")
            assertThat(awaitItem())
                .isEqualTo(MentionEditable.SearchQuery(13, ""))
        }
    }

    @Test
    fun `should move pass the whole span while moving cursor around mentioned block `() {
        mentionEditable.append("Mention @user here")
        mentionEditable.addMention(MentionViewModel.Member("user", "User", false), 8..14)

        // Put cursor right before @user, it should then select nothing
        Selection.setSelection(mentionEditable, 8)
        assertThat(mentionEditable.selection()).isEqualTo(intArrayOf(8, 8))

        // Put cursor right after '@', it should then select the whole @user
        Selection.setSelection(mentionEditable, 9)
        assertThat(mentionEditable.selection()).isEqualTo(intArrayOf(8, 13))

        // Put cursor right after @user, it should then select nothing
        Selection.setSelection(mentionEditable, 13)
        assertThat(mentionEditable.selection()).isEqualTo(intArrayOf(13, 13))
    }

    @Test
    fun `should delete the whole mention block while deleting only part of it`() {
        mentionEditable.append("Mention @user here")
        mentionEditable.addMention(MentionViewModel.Member("user", "User", false), 8..14)

        mentionEditable.delete(8, 9)
        assertThat(mentionEditable.toString()).isEqualTo("Mention  here")
    }
}

private fun CharSequence.selection(): IntArray {
    return intArrayOf(Selection.getSelectionStart(this), Selection.getSelectionEnd(this))
}

private fun Editable.simulateTyping(text: String) {
    this.append(text)
    Selection.setSelection(this, this.length)
}
