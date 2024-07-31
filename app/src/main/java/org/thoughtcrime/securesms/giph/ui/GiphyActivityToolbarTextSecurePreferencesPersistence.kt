package org.thoughtcrime.securesms.giph.ui

import org.session.libsession.messaging.MessagingModuleConfiguration.Companion.shared
import org.thoughtcrime.securesms.giph.ui.GiphyActivityToolbar.Persistence

class GiphyActivityToolbarTextSecurePreferencesPersistence: Persistence {
    override fun getGridSelected(): Boolean = shared.prefs.isGifSearchInGridLayout()
    override fun setGridSelected(isGridSelected: Boolean) = shared.prefs.setIsGifSearchInGridLayout(isGridSelected)
}
