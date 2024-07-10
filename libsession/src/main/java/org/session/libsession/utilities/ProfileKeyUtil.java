package org.session.libsession.utilities;

import static org.session.libsession.utilities.TextSecurePreferencesKt.getPrefs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.session.libsignal.utilities.Base64;

import java.io.IOException;

public class ProfileKeyUtil {

  public static final int PROFILE_KEY_BYTES = 32;

  public static synchronized @NonNull byte[] getProfileKey(@NonNull Context context) {
    try {
      String encodedProfileKey = MessagingModuleConfiguration.getShared().getPrefs().getProfileKey();

      if (encodedProfileKey == null) {
        encodedProfileKey = Util.getSecret(PROFILE_KEY_BYTES);
        MessagingModuleConfiguration.getShared().getPrefs().setProfileKey(encodedProfileKey);
      }

      return Base64.decode(encodedProfileKey);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static synchronized @NonNull byte[] getProfileKeyFromEncodedString(String encodedProfileKey) {
    try {
      return Base64.decode(encodedProfileKey);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static synchronized @NonNull String generateEncodedProfileKey(@NonNull Context context) {
    return Util.getSecret(PROFILE_KEY_BYTES);
  }

  public static synchronized void setEncodedProfileKey(@NonNull Context context, @Nullable String key) {
    MessagingModuleConfiguration.getShared().getPrefs().setProfileKey(key);
  }
}
