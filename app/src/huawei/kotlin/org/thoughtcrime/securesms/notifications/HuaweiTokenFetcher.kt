package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.huawei.hms.aaid.HmsInstanceId
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsignal.utilities.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val APP_ID = "107205081"
private const val TOKEN_SCOPE = "HCM"

@Singleton
class HuaweiTokenFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pushRegistry: Lazy<PushRegistry>,
): TokenFetcher {
    override suspend fun fetch(): String? = HmsInstanceId.getInstance(context).run {
        // https://developer.huawei.com/consumer/en/doc/development/HMS-Guides/push-basic-capability#h2-1576218800370
        // getToken may return an empty string, if so HuaweiPushService#onNewToken will be called.
        withContext(Dispatchers.IO) { getToken(APP_ID, TOKEN_SCOPE) }
    }
}
