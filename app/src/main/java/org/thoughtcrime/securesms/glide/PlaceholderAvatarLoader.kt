package org.thoughtcrime.securesms.glide

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import org.session.libsession.avatars.PlaceholderAvatarPhoto
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient

class PlaceholderAvatarLoader(private val appContext: Context): ModelLoader<PlaceholderAvatarPhoto, BitmapDrawable> {

    override fun buildLoadData(
        model: PlaceholderAvatarPhoto,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<BitmapDrawable> {
        val displayName: String = when {
            !model.displayName.isNullOrBlank() -> model.displayName.orEmpty()
            model.hashString == TextSecurePreferences.getLocalNumber(appContext) -> TextSecurePreferences.getProfileName(appContext).orEmpty()
            else -> Recipient.from(appContext, Address.fromSerialized(model.hashString), false).let {
                it.profileName ?: it.name ?: ""
            }
        }

        return LoadData(model, PlaceholderAvatarFetcher(appContext, model.hashString, displayName))
    }

    override fun handles(model: PlaceholderAvatarPhoto): Boolean = true

    class Factory(private val appContext: Context) : ModelLoaderFactory<PlaceholderAvatarPhoto, BitmapDrawable> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<PlaceholderAvatarPhoto, BitmapDrawable> {
            return PlaceholderAvatarLoader(appContext)
        }
        override fun teardown() {}
    }
}