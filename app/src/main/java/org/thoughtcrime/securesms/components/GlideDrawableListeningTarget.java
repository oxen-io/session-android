package org.thoughtcrime.securesms.components;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.request.target.DrawableImageViewTarget;

import org.session.libsignal.utilities.SettableFuture;

import java.lang.ref.WeakReference;

public class GlideDrawableListeningTarget extends DrawableImageViewTarget {

  private final SettableFuture<Boolean> loaded;
  private final WeakReference<View> loadingView;

  public GlideDrawableListeningTarget(@NonNull ImageView view, @Nullable View loadingView, @NonNull SettableFuture<Boolean> loaded) {
    super(view);
    this.loaded = loaded;
    this.loadingView = new WeakReference<View>(loadingView);
  }

  @Override
  protected void setResource(@Nullable Drawable resource) {
    super.setResource(resource);
    loaded.set(true);

    View loadingViewInstance = loadingView.get();

    if (loadingViewInstance != null) {
      loadingViewInstance.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoadFailed(@Nullable Drawable errorDrawable) {
    super.onLoadFailed(errorDrawable);
    loaded.set(true);

    View loadingViewInstance = loadingView.get();

    if (loadingViewInstance != null) {
      loadingViewInstance.setVisibility(View.GONE);
    }
  }
}
