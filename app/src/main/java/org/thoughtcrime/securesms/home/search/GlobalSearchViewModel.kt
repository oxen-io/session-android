package org.thoughtcrime.securesms.home.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.session.libsignal.utilities.SettableFuture
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.search.model.SearchResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {
    private val scope = viewModelScope + SupervisorJob()
    private val refreshes = MutableSharedFlow<Unit>()
    private val _queryText = MutableStateFlow<CharSequence>("")

    val result = _queryText
        .reEmit(refreshes)
        .buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .mapLatest { query ->
            if (query.trim().isEmpty()) {
                // searching for 05 as contactDb#getAllContacts was not returning contacts
                // without a nickname/name who haven't approved us.
                GlobalSearchResult(query.toString(), searchRepository.queryContacts("05").first.toList())
            } else {
                // User input delay in case we get a new query within a few hundred ms this
                // coroutine will be cancelled and the expensive query will not be run.
                delay(300)
                val settableFuture = SettableFuture<SearchResult>()
                searchRepository.query(query.toString(), settableFuture::set)
                try {
                    // search repository doesn't play nicely with suspend functions (yet)
                    settableFuture.get(10_000, TimeUnit.MILLISECONDS).toGlobalSearchResult()
                } catch (e: Exception) {
                    GlobalSearchResult(query.toString())
                }
            }
        }

    fun setQuery(charSequence: CharSequence) {
        _queryText.value = charSequence
    }

    fun refresh() {
        viewModelScope.launch {
            refreshes.emit(Unit)
        }
    }
}

/**
 * Re-emit whenever refreshes emits.
 * */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<T>.reEmit(refreshes: Flow<Unit>) = flatMapLatest { query -> merge(flowOf(query), refreshes.map { query }) }
