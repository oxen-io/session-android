package org.session.libsession.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;

import com.makeramen.roundedimageview.RoundedDrawable;

import org.session.libsession.R;
import org.session.libsession.utilities.ThemeUtil;

public class ResourceContactPhoto implements FallbackContactPhoto {

  private final int resourceId;

  public ResourceContactPhoto(@DrawableRes int resourceId) {
    this.resourceId = resourceId;
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return asDrawable(context, 0, false, 0f);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted, Float padding) {
    // rounded colored background
    GradientDrawable background = new GradientDrawable();
    background.setShape(GradientDrawable.OVAL);
    background.setColor(inverted ? Color.WHITE : color);

    // resource image in the foreground
    RoundedDrawable foreground = (RoundedDrawable) RoundedDrawable.fromDrawable(AppCompatResources.getDrawable(context, resourceId));

    if (foreground != null) {
      if(padding == 0f){
        foreground.setScaleType(ImageView.ScaleType.CENTER_CROP);
      } else {
        // apply padding via a transparent border oterhwise things get misaligned
        foreground.setScaleType(ImageView.ScaleType.FIT_CENTER);
        foreground.setBorderColor(Color.TRANSPARENT);
        foreground.setBorderWidth(padding);
      }

      if (inverted) {
        foreground.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
      }
    }

    Drawable gradient = AppCompatResources.getDrawable(
            context,
            ThemeUtil.isDarkTheme(context) ? R.drawable.avatar_gradient_dark : R.drawable.avatar_gradient_light
    );

    return new ExpandingLayerDrawable(new Drawable[] {background, foreground, gradient});
  }

  private static class ExpandingLayerDrawable extends LayerDrawable {
    public ExpandingLayerDrawable(Drawable[] layers) {
      super(layers);
    }

    @Override
    public int getIntrinsicWidth() {
      return -1;
    }

    @Override
    public int getIntrinsicHeight() {
      return -1;
    }
  }

}
