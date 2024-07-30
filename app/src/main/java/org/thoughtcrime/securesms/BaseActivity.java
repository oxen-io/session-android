package org.thoughtcrime.securesms;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.fragment.app.FragmentActivity;

import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageActivityHelper;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageContextWrapper;

import network.loki.messenger.R;

public abstract class BaseActivity extends FragmentActivity {
  @Override
  protected void onResume() {
    super.onResume();
    DynamicLanguageActivityHelper.recreateIfNotInCorrectLanguage(this, TextSecurePreferences.getLanguage(this));
    String name = getResources().getString(R.string.sessionMessenger);
    Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_foreground);
    int color = getResources().getColor(R.color.app_icon_background);
    setTaskDescription(new ActivityManager.TaskDescription(name, icon, color));
  }

  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(newBase, TextSecurePreferences.getLanguage(newBase)));
  }
}
