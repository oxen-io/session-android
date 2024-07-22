package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import network.loki.messenger.R
import network.loki.messenger.databinding.ThumbnailViewBinding
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.utilities.Util.equals
import org.session.libsignal.utilities.ListenableFuture
import org.session.libsignal.utilities.SettableFuture
import org.thoughtcrime.securesms.components.GlideBitmapListeningTarget
import org.thoughtcrime.securesms.components.GlideDrawableListeningTarget
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri
import org.thoughtcrime.securesms.mms.GlideRequest
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.Slide

open class ThumbnailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val WIDTH = 0
        private const val HEIGHT = 1
    }

    private val binding: ThumbnailViewBinding by lazy { ThumbnailViewBinding.bind(this) }

    // region Lifecycle

    val loadIndicator: View by lazy { binding.thumbnailLoadIndicator }

    private val dimensDelegate = ThumbnailDimensDelegate()

    private var slide: Slide? = null

    init {
        attrs?.let { context.theme.obtainStyledAttributes(it, R.styleable.ThumbnailView, 0, 0) }
            ?.apply {
                dimensDelegate.setBounds(
                    getDimensionPixelSize(R.styleable.ThumbnailView_minWidth, 0),
                    getDimensionPixelSize(R.styleable.ThumbnailView_minHeight, 0),
                    getDimensionPixelSize(R.styleable.ThumbnailView_maxWidth, 0),
                    getDimensionPixelSize(R.styleable.ThumbnailView_maxHeight, 0)
                )

                setRoundedCorners(
                    getDimensionPixelSize(R.styleable.ThumbnailView_thumbnail_radius, 0)
                )

                recycle()
            }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val adjustedDimens = dimensDelegate.resourceSize()
        if (adjustedDimens[WIDTH] == 0 && adjustedDimens[HEIGHT] == 0) {
            return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        val finalWidth: Int = adjustedDimens[WIDTH] + paddingLeft + paddingRight
        val finalHeight: Int = adjustedDimens[HEIGHT] + paddingTop + paddingBottom

        super.onMeasure(
            MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY)
        )
    }

    private fun getDefaultWidth() = maxOf(layoutParams?.width ?: 0, 0)
    private fun getDefaultHeight() = maxOf(layoutParams?.height ?: 0, 0)

    // endregion

    // region Interaction
    fun setRoundedCorners(radius: Int){
        // create an outline provider and clip the whole view to that shape
        // that way we can round the image and the background ( and any other artifacts that the view may contain )
        val mOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // all corners
                outline.setRoundRect(0, 0, view.width, view.height, radius.toFloat())
            }
        }

        outlineProvider = mOutlineProvider
        clipToOutline = true
    }

    fun setImageResource(
        glide: GlideRequests,
        slide: Slide,
        isPreview: Boolean
    ): ListenableFuture<Boolean> = setImageResource(glide, slide, isPreview, 0, 0)

    fun setImageResource(
        glide: GlideRequests, slide: Slide,
        isPreview: Boolean, naturalWidth: Int,
        naturalHeight: Int
    ): ListenableFuture<Boolean> {
        binding.playOverlay.isVisible = (slide.thumbnailUri != null && slide.hasPlayOverlay() &&
            (slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_DONE || isPreview))

        if (equals(this.slide, slide)) {
            // don't re-load slide
            return SettableFuture(false)
        }

        this.slide = slide

        binding.thumbnailLoadIndicator.isVisible = slide.isInProgress
        binding.thumbnailDownloadIcon.isVisible =
            slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED

        dimensDelegate.setDimens(naturalWidth, naturalHeight)
        invalidate()

        return SettableFuture<Boolean>().also {
            when {
                slide.thumbnailUri != null -> {
                    buildThumbnailGlideRequest(glide, slide).into(
                        GlideDrawableListeningTarget(binding.thumbnailImage, binding.thumbnailLoadIndicator, it)
                    )
                }
                slide.hasPlaceholder() -> {
                    buildPlaceholderGlideRequest(glide, slide).into(
                        GlideBitmapListeningTarget(binding.thumbnailImage, null, it)
                    )
                }
                else -> {
                    glide.clear(binding.thumbnailImage)
                    it.set(false)
                }
            }
        }
    }

    private fun buildThumbnailGlideRequest(
        glide: GlideRequests,
        slide: Slide
    ): GlideRequest<Drawable> = glide.load(DecryptableUri(slide.thumbnailUri!!))
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .overrideDimensions()
        .transition(DrawableTransitionOptions.withCrossFade())
        .transform(CenterCrop())
        .missingThumbnailPicture(slide.isInProgress)

    private fun buildPlaceholderGlideRequest(
        glide: GlideRequests,
        slide: Slide
    ): GlideRequest<Bitmap> = glide.asBitmap()
        .load(slide.getPlaceholderRes(context.theme))
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .overrideDimensions()
        .fitCenter()

    open fun clear(glideRequests: GlideRequests) {
        glideRequests.clear(binding.thumbnailImage)
        slide = null
    }

    fun setImageResource(
        glideRequests: GlideRequests,
        uri: Uri
    ): ListenableFuture<Boolean> = glideRequests.load(DecryptableUri(uri))
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .transition(DrawableTransitionOptions.withCrossFade())
        .transform(CenterCrop())
        .intoDrawableTargetAsFuture()

    private fun GlideRequest<Drawable>.intoDrawableTargetAsFuture() =
        SettableFuture<Boolean>().also {
            binding.run {
                GlideDrawableListeningTarget(thumbnailImage, thumbnailLoadIndicator, it)
            }.let { into(it) }
        }

    private fun <T> GlideRequest<T>.overrideDimensions() =
        dimensDelegate.resourceSize().takeIf { 0 !in it }
            ?.let { override(it[WIDTH], it[HEIGHT]) }
            ?: override(getDefaultWidth(), getDefaultHeight())
}

private fun <T> GlideRequest<T>.missingThumbnailPicture(
    inProgress: Boolean
) = takeIf { inProgress } ?: apply(RequestOptions.errorOf(R.drawable.ic_missing_thumbnail_picture))
