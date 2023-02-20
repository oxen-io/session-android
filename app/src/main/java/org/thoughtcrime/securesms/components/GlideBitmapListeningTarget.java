package org.thoughtcrime.securesms.components;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.request.target.BitmapImageViewTarget;

import org.session.libsignal.utilities.SettableFuture;

import java.lang.ref.WeakReference;

public class GlideBitmapListeningTarget extends BitmapImageViewTarget {

  private final SettableFuture<Boolean> loaded;
  private final WeakReference<View> loadingView;

  public GlideBitmapListeningTarget(@NonNull ImageView view, @Nullable View loadingView, @NonNull SettableFuture<Boolean> loaded) {
    super(view);
    this.loaded = loaded;
    this.loadingView = new WeakReference<View>(loadingView);
  }

  @Override
  protected void setResource(@Nullable Bitmap resource) {
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
