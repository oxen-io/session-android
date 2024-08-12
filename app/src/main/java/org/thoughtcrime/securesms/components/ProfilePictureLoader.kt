package org.thoughtcrime.securesms.components

import android.app.Application
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.glide.ContactPhotoFetcher
import java.io.InputStream

/**
 * The ProfilePictureLoader is responsible for a single recipient.
 *
 * It accepts either an Address or a Recipient object.
 */
class ProfilePictureLoader(
    private val application: Application
) : ModelLoader<Any, InputStream> {
    override fun buildLoadData(
        model: Any,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        val recipient = when (model) {
            is Address -> Recipient.from(application, model, false)
            is Recipient -> model
            else -> return null
        }

        val signalProfilePicture = recipient.contactPhoto

        if (signalProfilePicture != null) {
            return ModelLoader.LoadData(
                signalProfilePicture,
                ContactPhotoFetcher(application, signalProfilePicture)
            )
        }

        return null
    }

    override fun handles(model: Any): Boolean {
        return model is Address || model is Recipient
    }

    class Factory(private val application: Application) :
        ModelLoaderFactory<Any, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Any, InputStream> {
            return ProfilePictureLoader(application)
        }

        override fun teardown() {

        }
    }
}