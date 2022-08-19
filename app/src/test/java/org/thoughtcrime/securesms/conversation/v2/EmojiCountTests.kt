package org.thoughtcrime.securesms.conversation.v2

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.conversation.v2.messages.EmojiReactionsView


class EmojiCountTests {

    @Test
    fun `it should display numbers less than 1000 as they are`() {
        val formatString = EmojiReactionsView.getFormattedCount(900)
        assertEquals("900", formatString)
    }

    @Test
    fun `it should display exactly 1000 as 1k`() {
        val formatString = EmojiReactionsView.getFormattedCount(1000)
        assertEquals("1k", formatString)
    }

    @Test
    fun `it should display numbers less than 10_000 properly`() {
        val formatString = EmojiReactionsView.getFormattedCount(1300)
        assertEquals("1.3k", formatString)
        val multipleKFormatString = EmojiReactionsView.getFormattedCount(3100)
        assertEquals("3.1k", multipleKFormatString)
    }

    @Test
    fun `it should display zero properly`() {
        val formatString = EmojiReactionsView.getFormattedCount(0)
        assertEquals("0", formatString)
    }

    @Test
    fun `it shouldn't care about negative numbers`() {
        val formatString = EmojiReactionsView.getFormattedCount(-10)
        assertEquals("-10", formatString)
    }

    @Test
    fun `it shouldn't get about large negative numbers`() {
        val formatString = EmojiReactionsView.getFormattedCount(-1200)
        assertEquals("-1.2k", formatString)
    }

    @Test
    fun `it should display numbers above 10k properly`() {
        val formatString = EmojiReactionsView.getFormattedCount(12300)
        assertEquals("12.3k", formatString)
    }

    @Test
    fun `it should display numbers above 100k properly`() {
        val formatString = EmojiReactionsView.getFormattedCount(132560)
        assertEquals("132.5k", formatString)
    }

}