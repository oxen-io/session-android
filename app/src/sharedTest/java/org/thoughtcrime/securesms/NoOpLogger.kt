package org.thoughtcrime.securesms

import org.session.libsignal.utilities.Log.Logger

object NoOpLogger: Logger() {
    override fun v(tag: String?, message: String?, t: Throwable?) {}

    override fun d(tag: String?, message: String?, t: Throwable?) {}

    override fun i(tag: String?, message: String?, t: Throwable?) {}

    override fun w(tag: String?, message: String?, t: Throwable?) {}

    override fun e(tag: String?, message: String?, t: Throwable?) {}

    override fun wtf(tag: String?, message: String?, t: Throwable?) {}

    override fun blockUntilAllWritesFinished() {}
}