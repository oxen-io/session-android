package org.session.libsession.messaging.jobs

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.jobs.Job.Companion.MAX_BUFFER_SIZE
import org.session.libsession.messaging.sending_receiving.notifications.Server
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.snode.Version
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.retryIfNeeded

class NotifyPNServerJob(val message: SnodeMessage) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    override val maxFailureCount: Int = 20
    companion object {
        val KEY: String = "NotifyPNServerJob"

        // Keys used for database storage
        private val MESSAGE_KEY = "message"
    }

    override suspend fun execute(dispatcherName: String) {
        val server = Server.LEGACY
        val parameters = mapOf( "data" to message.data, "send_to" to message.recipient )
        val url = "${server.url}/notify"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        retryIfNeeded(4) {
            OnionRequestAPI.sendOnionRequest(
                request,
                server.url,
                server.publicKey,
                Version.V2
            ) success { response ->
                when (response.code) {
                    null, 0 -> Log.d("NotifyPNServerJob", "Couldn't notify PN server due to error: ${response.message}.")
                }
            } fail { exception ->
                Log.d("NotifyPNServerJob", "Couldn't notify PN server due to error: $exception.")
            }
        } success {
            handleSuccess(dispatcherName)
        } fail {
            handleFailure(dispatcherName, it)
        }
    }

    private fun handleSuccess(dispatcherName: String) {
        delegate?.handleJobSucceeded(this, dispatcherName)
    }

    private fun handleFailure(dispatcherName: String, error: Exception) {
        delegate?.handleJobFailed(this, dispatcherName, error)
    }

    override fun serialize(): Data {
        val kryo = Kryo()
        kryo.isRegistrationRequired = false
        val serializedMessage = ByteArray(4096)
        val output = Output(serializedMessage, MAX_BUFFER_SIZE)
        kryo.writeObject(output, message)
        output.close()
        return Data.Builder()
            .putByteArray(MESSAGE_KEY, serializedMessage)
            .build();
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory : Job.Factory<NotifyPNServerJob> {

        override fun create(data: Data): NotifyPNServerJob {
            val serializedMessage = data.getByteArray(MESSAGE_KEY)
            val kryo = Kryo()
            kryo.isRegistrationRequired = false
            val input = Input(serializedMessage)
            val message = kryo.readObject(input, SnodeMessage::class.java)
            input.close()
            return NotifyPNServerJob(message)
        }
    }
}