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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.VersionTracker.getLastSeenVersion
import org.thoughtcrime.securesms.util.VersionTracker.updateLastSeenVersion

class DatabaseUpgradeActivity : BaseActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateLastSeenVersion(this)
        lifecycleScope.launch {
            ApplicationContext.getInstance(this@DatabaseUpgradeActivity)
                .messageNotifier.updateNotification(this@DatabaseUpgradeActivity)
        }
        startActivity(intent.getParcelableExtra<Parcelable>("next_intent") as? Intent)
        finish()
    }

    companion object {
        @JvmStatic
        fun isUpdate(context: Context?): Boolean {
            val currentVersionCode = Util.getCanonicalVersionCode()
            val previousVersionCode = getLastSeenVersion(context!!)

            return previousVersionCode < currentVersionCode
        }
    }
}
