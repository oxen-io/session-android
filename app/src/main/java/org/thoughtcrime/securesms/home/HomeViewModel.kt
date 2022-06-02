package org.thoughtcrime.securesms.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.DatabaseContentProviders.ConversationList.CONTENT_URI
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val threadDb: ThreadDatabase): ViewModel() {

    private val executor = viewModelScope + SupervisorJob()

    private val _conversations = MutableLiveData<List<ThreadRecord>>()
    val conversations: LiveData<List<ThreadRecord>> = _conversations

    fun getObservable(context: Context): LiveData<List<ThreadRecord>> {
        executor.launch(Dispatchers.IO) {
            context.contentResolver
                .observeQuery(CONTENT_URI)
                .map {
                    threadDb.approvedConversationList.use { openCursor ->
                        val reader = threadDb.readerFor(openCursor)
                        val threads = mutableListOf<ThreadRecord>()
                        while (true) {
                            threads += reader.next ?: break
                        }
                        threads
                    }
                }
                .onEach { newConversations ->
                    withContext(Dispatchers.Main) {
                        _conversations.value = newConversations
                    }
                }
                .collect()
        }
        return conversations
    }

}