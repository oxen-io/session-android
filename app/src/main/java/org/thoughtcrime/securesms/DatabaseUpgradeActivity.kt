/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.os.Parcelable
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.VersionTracker.getLastSeenVersion
import org.thoughtcrime.securesms.util.VersionTracker.updateLastSeenVersion

class DatabaseUpgradeActivity : BaseActivity() {
    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        updateLastSeenVersion(this)
        updateNotifications(this)
        startActivity(intent.getParcelableExtra<Parcelable>("next_intent") as Intent?)
        finish()
    }

    @SuppressLint("StaticFieldLeak")
    private fun updateNotifications(context: Context) {
        object : AsyncTask<Void?, Void?, Void?>() {
            protected override fun doInBackground(vararg params: Void): Void? {
                ApplicationContext.getInstance(context).messageNotifier.updateNotification(context)
                return null
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    companion object {
        fun isUpdate(context: Context?): Boolean {
            val currentVersionCode = Util.getCanonicalVersionCode()
            val previousVersionCode = getLastSeenVersion(context!!)

            return previousVersionCode < currentVersionCode
        }
    }
}
