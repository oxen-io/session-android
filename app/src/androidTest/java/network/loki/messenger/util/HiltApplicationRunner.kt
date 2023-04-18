package network.loki.messenger.util

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import network.loki.messenger.HiltApplicationContext

class HiltApplicationRunner: AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltApplicationContext::class.java.name, context)
    }
}