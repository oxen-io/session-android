@file:JvmName("FcmHcmUtils")
package org.thoughtcrime.securesms.notifications

import com.google.android.gms.tasks.Task
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.huawei.hms.aaid.HmsInstanceId
import kotlinx.coroutines.*
import android.content.Context


fun getFcmInstanceId(body: (Task<InstanceIdResult>)->Unit): Job = MainScope().launch(Dispatchers.IO) {
    val task = FirebaseInstanceId.getInstance().instanceId
    while (!task.isComplete && isActive) {
        // wait for task to complete while we are active
    }
    if (!isActive) return@launch // don't 'complete' task if we were canceled
    withContext(Dispatchers.Main) {
        body(task)
    }
}

fun getHcmInstanceId(context: Context, body: (HmsInstanceId)->Unit): Job = MainScope().launch(Dispatchers.IO) {
    val hmsInstanceId = HmsInstanceId.getInstance(context)
    if (!isActive) return@launch // don't 'complete' task if we were canceled
    withContext(Dispatchers.Main) {
        body(hmsInstanceId)
    }
}