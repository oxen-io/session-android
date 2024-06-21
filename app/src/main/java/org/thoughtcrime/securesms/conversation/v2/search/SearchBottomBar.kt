package org.thoughtcrime.securesms.conversation.v2.search

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewSearchBottomBarBinding
import org.session.util.StringSubstitutionConstants.COUNT_KEY
import org.session.util.StringSubstitutionConstants.QUERY_KEY
import org.session.util.StringSubstitutionConstants.TOTAL_COUNT_KEY

class SearchBottomBar : LinearLayout {
    private lateinit var binding: ViewSearchBottomBarBinding
    private var eventListener: EventListener? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    fun initialize() {
        binding = ViewSearchBottomBarBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun setData(position: Int, count: Int, query: String?) = with(binding) {
        searchProgressWheel.visibility = GONE
        searchUp.setOnClickListener { v: View? ->
            if (eventListener != null) {
                eventListener!!.onSearchMoveUpPressed()
            }
        }
        searchDown.setOnClickListener { v: View? ->
            if (eventListener != null) {
                eventListener!!.onSearchMoveDownPressed()
            }
        }

        // If we found search results list how many we found
        if (count > 0) {
            searchPosition.text = Phrase.from(context, R.string.searchMatches)
                .put(COUNT_KEY, position + 1)
                .put(TOTAL_COUNT_KEY, count)
                .format()
        } else {
            // If there are no results we don't display anything if the query is
            // empty, but we'll substitute "No results found for <query>" otherwise.
            var txt = ""
            if (query != null) {
                if (query.isNotEmpty()) {
                    txt = Phrase.from(context, R.string.searchMatchesNoneSpecific)
                        .put(QUERY_KEY, query)
                        .format().toString()
                }
            }
            searchPosition.text = txt
        }
        setViewEnabled(searchUp, position < count - 1)
        setViewEnabled(searchDown, position > 0)
    }

    fun showLoading() {
        binding.searchProgressWheel.visibility = VISIBLE
    }

    private fun setViewEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.25f
    }

    fun setEventListener(eventListener: EventListener?) {
        this.eventListener = eventListener
    }

    interface EventListener {
        fun onSearchMoveUpPressed()
        fun onSearchMoveDownPressed()
    }
}