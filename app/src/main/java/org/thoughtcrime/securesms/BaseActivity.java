package org.thoughtcrime.securesms;

import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.fragment.app.FragmentActivity;

import network.loki.messenger.R;

public abstract class BaseActivity extends FragmentActivity {
  @Override
  protected void onResume() {
    super.onResume();
    String name = getResources().getString(R.string.app_name);
    Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_foreground);
    int color = getResources().getColor(R.color.app_icon_background);
    setTaskDescription(new ActivityManager.TaskDescription(name, icon, color));
  }
}
