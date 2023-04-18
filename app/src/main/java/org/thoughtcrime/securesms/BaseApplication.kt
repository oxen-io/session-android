package org.thoughtcrime.securesms

import android.app.Application
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject

abstract class BaseApplication: Application() {
    lateinit var lokiAPIDatabase: LokiAPIDatabase
    lateinit var storage: Storage
    lateinit var messageDataProvider: MessageDataProvider
    lateinit var jobDatabase: JobDatabase
    lateinit var textSecurePreferences: TextSecurePreferences
    lateinit var configFactory: ConfigFactory
}