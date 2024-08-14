package org.session.libsession.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.makeramen.roundedimageview.RoundedDrawable;

public class TransparentContactPhoto implements FallbackContactPhoto {

  public TransparentContactPhoto() {}

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return asDrawable(context, color, inverted, 0f);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted, Float padding) {
    return RoundedDrawable.fromDrawable(ContextCompat.getDrawable(context, android.R.color.transparent));
  }

}
