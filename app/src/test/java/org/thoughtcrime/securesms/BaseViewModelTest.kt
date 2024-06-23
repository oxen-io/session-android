package org.thoughtcrime.securesms

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.BeforeClass
import org.junit.Rule
import org.session.libsignal.utilities.Log

open class BaseViewModelTest: BaseCoroutineTest() {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupLogger() {
            Log.initialize(NoOpLogger)
        }
    }

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

}