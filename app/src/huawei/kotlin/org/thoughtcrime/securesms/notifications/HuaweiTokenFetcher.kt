package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.huawei.hms.aaid.HmsInstanceId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.messaging.notifications.TokenFetcher
import javax.inject.Inject
import javax.inject.Singleton

private const val APP_ID = "107205081"
private const val TOKEN_SCOPE = "HCM"

@Singleton
class HuaweiTokenFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
): TokenFetcher {
    override val token = MutableStateFlow<String?>(null)

    override fun onNewToken(token: String) {
        this.token.value = token
    }

    init {
        GlobalScope.launch {
            val instanceId = HmsInstanceId.getInstance(context)
            withContext(Dispatchers.Default) {
                instanceId.getToken(APP_ID, TOKEN_SCOPE)
            }
        }

    }
}
