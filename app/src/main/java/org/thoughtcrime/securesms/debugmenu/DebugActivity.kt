package org.thoughtcrime.securesms.debugmenu

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.thoughtcrime.securesms.ui.setComposeContent


@AndroidEntryPoint
class DebugActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            DebugMenuScreen(
                onClose = { finish() }
            )
        }
    }
}
