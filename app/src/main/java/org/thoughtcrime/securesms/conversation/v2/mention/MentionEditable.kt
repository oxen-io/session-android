package org.thoughtcrime.securesms.conversation.v2.mention

import android.text.Selection
import android.text.SpannableStringBuilder
import androidx.core.text.getSpans
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private const val SEARCH_QUERY_DEBOUNCE_MILLS = 100L

/**
 * A subclass of [SpannableStringBuilder] that provides a way to observe the mention search query,
 * and also manages the [MentionSpan] in a way that treats the mention span as a whole.
 */
class MentionEditable : SpannableStringBuilder() {
    private val queryChangeNotification = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    fun observeMentionSearchQuery(): Flow<SearchQuery?> {
        @Suppress("OPT_IN_USAGE")
        return queryChangeNotification
            .debounce(SEARCH_QUERY_DEBOUNCE_MILLS)
            .onStart { emit(Unit) }
            .map { mentionSearchQuery }
            .distinctUntilChanged()
    }

    data class SearchQuery(
        val mentionSymbolStartAt: Int,
        val query: String
    )

    val mentionSearchQuery: SearchQuery?
        get() {
            val cursorPosition = Selection.getSelectionStart(this)

            // First, make sure we are not selecting text
            if (cursorPosition != Selection.getSelectionEnd(this)) {
                return null
            }

            // Make sure we don't already have a mention span at the cursor position
            if (getSpans(cursorPosition, cursorPosition, MentionSpan::class.java).isNotEmpty()) {
                return null
            }

            // Find the mention symbol '@' before the cursor position
            val symbolIndex = findEligibleMentionSymbolIndexBefore(cursorPosition - 1)
            if (symbolIndex < 0) {
                return null
            }

            // The query starts after the symbol '@' and ends at a whitespace, @ or the end
            val queryStart = symbolIndex + 1
            var queryEnd = indexOfStartingAt(queryStart) { it.isWhitespace() || it == '@' }
            if (queryEnd < 0) {
                queryEnd = length
            }

            return SearchQuery(
                mentionSymbolStartAt = symbolIndex,
                query = subSequence(queryStart, queryEnd).toString()
            )
        }

    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
        var normalisedStart = start
        var normalisedEnd = end

        val isSelectionStart = what == Selection.SELECTION_START
        val isSelectionEnd = what == Selection.SELECTION_END

        if (isSelectionStart || isSelectionEnd) {
            assert(start == end) { "Selection spans must have zero length" }
            val selection = start

            val mentionSpan = getSpans<MentionSpan>(selection, selection).firstOrNull()
            if (mentionSpan != null) {
                val spanStart = getSpanStart(mentionSpan)
                val spanEnd = getSpanEnd(mentionSpan)

                if (isSelectionStart && selection != spanEnd) {
                    // A selection start will only be adjusted to the start of the mention span,
                    // if the selection start is not at the end the mention span. (A selection start
                    // at the end of the mention span is considered an escape path from the mention span)
                    normalisedStart = spanStart
                    normalisedEnd = normalisedStart
                } else if (isSelectionEnd && selection != spanStart) {
                    normalisedEnd = spanEnd
                    normalisedStart = normalisedEnd
                }
            }

            queryChangeNotification.tryEmit(Unit)
        }

        super.setSpan(what, normalisedStart, normalisedEnd, flags)
    }

    override fun removeSpan(what: Any?) {
        super.removeSpan(what)
        queryChangeNotification.tryEmit(Unit)
    }

    // The only method we need to override
    override fun replace(st: Int, en: Int, source: CharSequence?, start: Int, end: Int): MentionEditable {
        // Make sure the mention span is treated like a whole
        var normalisedStart = st
        var normalisedEnd = en

        if (st != en) {
            // Find the mention span that intersects with the replaced range, and expand the range to include it,
            // this does not apply to insertion operation (st == en)
            for (mentionSpan in getSpans(st, en, MentionSpan::class.java)) {
                val mentionStart = getSpanStart(mentionSpan)
                val mentionEnd = getSpanEnd(mentionSpan)

                if (mentionStart < normalisedStart) {
                    normalisedStart = mentionStart
                }
                if (mentionEnd > normalisedEnd) {
                    normalisedEnd = mentionEnd
                }

                removeSpan(mentionSpan)
            }
        }

        super.replace(normalisedStart, normalisedEnd, source, start, end)
        queryChangeNotification.tryEmit(Unit)
        return this
    }

    fun addMention(member: MentionViewModel.Member, replaceRange: IntRange) {
        val replaceWith = "@${member.name} "
        replace(replaceRange.first, replaceRange.last, replaceWith)
        setSpan(
            MentionSpan(member),
            replaceRange.first,
            replaceRange.first + replaceWith.length - 1,
            SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    override fun delete(st: Int, en: Int) = replace(st, en, "", 0, 0)

    private fun findEligibleMentionSymbolIndexBefore(offset: Int): Int {
        if (isEmpty()) {
            return -1
        }

        var i = offset.coerceIn(indices)
        while (i >= 0) {
            val c = get(i)
            if (c == '@') {
                // Make sure there is no more '@' before this one or it's disqualified
                if (i > 0 && get(i - 1) == '@') {
                    return -1
                }

                return i
            } else if (c.isWhitespace()) {
                break
            }
            i--
        }
        return -1
    }
}

private fun CharSequence.indexOfStartingAt(offset: Int, predicate: (Char) -> Boolean): Int {
    var i = offset.coerceIn(0..length)
    while (i < length) {
        if (predicate(get(i))) {
            return i
        }
        i++
    }

    return -1
}
