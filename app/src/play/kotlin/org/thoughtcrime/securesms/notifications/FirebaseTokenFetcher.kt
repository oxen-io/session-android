package org.thoughtcrime.securesms.notifications

import com.google.android.gms.tasks.Tasks
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTokenFetcher @Inject constructor(): TokenFetcher {
    override suspend fun fetch() = withContext(Dispatchers.IO) {
        FirebaseInstanceId.getInstance().instanceId
            .also(Tasks::await)
            .takeIf { isActive } // don't 'complete' task if we were canceled
            ?.run { result?.token ?: throw exception!! }
    }
}
