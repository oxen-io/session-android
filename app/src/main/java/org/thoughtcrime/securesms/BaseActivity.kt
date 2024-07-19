package org.thoughtcrime.securesms

import android.app.ActivityManager.TaskDescription
import android.content.Context
import android.graphics.BitmapFactory
import androidx.fragment.app.FragmentActivity
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration.Companion.shared
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageActivityHelper
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageContextWrapper

abstract class BaseActivity : FragmentActivity() {
    override fun onResume() {
        super.onResume()
        DynamicLanguageActivityHelper.recreateIfNotInCorrectLanguage(
            this,
            shared.prefs.getLanguage()
        )
        val name = resources.getString(R.string.app_name)
        val icon = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)
        val color = resources.getColor(R.color.app_icon_background)
        setTaskDescription(TaskDescription(name, icon, color))
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            DynamicLanguageContextWrapper.updateContext(
                newBase,
                shared.prefs.getLanguage()
            )
        )
    }
}
