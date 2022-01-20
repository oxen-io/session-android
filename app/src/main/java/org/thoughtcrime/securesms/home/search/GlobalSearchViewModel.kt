package org.thoughtcrime.securesms.home.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import org.session.libsession.utilities.concurrent.SignalExecutors
import org.session.libsignal.utilities.SettableFuture
import org.thoughtcrime.securesms.contacts.ContactAccessor
import org.thoughtcrime.securesms.database.SearchDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.search.model.SearchResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
        @ApplicationContext context: Context,
        searchDb: SearchDatabase,
        threadDb: ThreadDatabase,
        contactDb: SessionContactDatabase
) : ViewModel() {

    private val executor = viewModelScope + SupervisorJob()

    private val searchRepository = SearchRepository(
            context,
            searchDb,
            threadDb,
            contactDb,
            ContactAccessor.getInstance(),
            SignalExecutors.SERIAL
    )

    private val _result: MutableStateFlow<GlobalSearchResult> =
            MutableStateFlow(GlobalSearchResult.EMPTY)

    val result: StateFlow<GlobalSearchResult> = _result

    private val _queryText: MutableStateFlow<CharSequence> = MutableStateFlow("")

    fun postQuery(charSequence: CharSequence?) {
        charSequence ?: return
        _queryText.value = charSequence
    }

    init {
        //
        _queryText
                .buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .mapLatest { query ->
                    if (query.trim().length < 2) {
                        SearchResult.EMPTY
                    } else {
                        val settableFuture = SettableFuture<SearchResult>()
                        searchRepository.query(query.toString(), settableFuture::set)
                        try {
                            // search repository doesn't play nicely with suspend functions (yet)
                            settableFuture.get(10_000, TimeUnit.MILLISECONDS)
                        } catch (e: Exception) {
                            SearchResult.EMPTY
                        }
                    }
                }
                .onEach { result ->
                    // update the latest _result value
                    _result.value = GlobalSearchResult.from(result)
                }
                .launchIn(executor)
    }


}