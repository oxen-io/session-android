package org.thoughtcrime.securesms.crypto

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MnemonicLoader(private val appContext: Context) {
    suspend fun loadFileContents(fileName: String): String {
        return withContext(Dispatchers.IO) {
            appContext.assets.open("mnemonic/$fileName.txt").use {
                it.reader().readText()
            }
        }
    }
}
