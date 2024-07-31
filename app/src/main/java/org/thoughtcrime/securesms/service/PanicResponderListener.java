package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.TextSecurePreferencesKt;
import org.thoughtcrime.securesms.ApplicationContext;

/**
 * Respond to a PanicKit trigger Intent by locking the app.  PanicKit provides a
 * common framework for creating "panic button" apps that can trigger actions
 * in "panic responder" apps.  In this case, the response is to lock the app,
 * if it has been configured to do so via the Signal lock preference. If the
 * user has not set a passphrase, then the panic trigger intent does nothing.
 */
public class PanicResponderListener extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    TextSecurePreferences prefs = ApplicationContext.getInstance(context).getPrefs();
    if (intent != null  && !prefs.isPasswordDisabled() &&
        "info.guardianproject.panic.action.TRIGGER".equals(intent.getAction()))
    {
      Intent lockIntent = new Intent(context, KeyCachingService.class);
      lockIntent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
      context.startService(lockIntent);
    }
  }
}