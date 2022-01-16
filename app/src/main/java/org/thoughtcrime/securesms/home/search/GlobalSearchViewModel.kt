package org.thoughtcrime.securesms.home.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import org.session.libsession.utilities.concurrent.SignalExecutors
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.SettableFuture
import org.thoughtcrime.securesms.contacts.ContactAccessor
import org.thoughtcrime.securesms.database.SearchDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.search.model.SearchResult
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

    private val _queryText: MutableStateFlow<CharSequence> = MutableStateFlow("")

    fun postQuery(charSequence: CharSequence) {
        _queryText.value = charSequence // TODO: refactor to use new kotlin coroutines when #824 is merged
    }

    init {
        _queryText
//             .debounce(1_000L) // may need to debounce for performance improvements
            .map { query ->
                if (query.trim().length < 2) return@map GlobalSearchResult.EMPTY
                val settableFuture = SettableFuture<SearchResult>()
                searchRepository.query(query.toString(), settableFuture::set)
                val result = try {
                    // search repository doesn't play nicely with suspend functions (yet)
                    settableFuture.get()
                } catch (e: Exception) {
                    GlobalSearchResult.EMPTY
                }
                val conversations = result
                GlobalSearchResult.EMPTY
            }
            .onEach { result ->
                Log.d("Loki-test", "Result contacts: ${result.contacts.joinToString { it.name.orEmpty() }}")
                Log.d("Loki-test", "Result group threads: ${result.threads.joinToString { it.recipient.name.orEmpty() }}")
                Log.d("Loki-test", "Result messages: ${result.messages.joinToString { message -> message.bodySnippet }}")
            }
            .launchIn(executor)
    }



}