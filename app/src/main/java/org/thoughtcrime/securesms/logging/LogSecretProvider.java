package org.thoughtcrime.securesms.logging;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;

import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.thoughtcrime.securesms.crypto.KeyStoreHelper;
import org.session.libsignal.utilities.Base64;
import org.session.libsession.utilities.TextSecurePreferences;

import java.io.IOException;
import java.security.SecureRandom;

class LogSecretProvider {

  static byte[] getOrCreateAttachmentSecret(@NonNull Context context) {
    String unencryptedSecret = MessagingModuleConfiguration.getShared().getPrefs().getLogUnencryptedSecret();
    String encryptedSecret   = MessagingModuleConfiguration.getShared().getPrefs().getLogEncryptedSecret();

    if      (unencryptedSecret != null) return parseUnencryptedSecret(unencryptedSecret);
    else if (encryptedSecret != null)   return parseEncryptedSecret(encryptedSecret);
    else                                return createAndStoreSecret(context);
  }

  private static byte[] parseUnencryptedSecret(String secret) {
    try {
      return Base64.decode(secret);
    } catch (IOException e) {
      throw new AssertionError("Failed to decode the unecrypted secret.");
    }
  }

  private static byte[] parseEncryptedSecret(String secret) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(secret);
      return KeyStoreHelper.unseal(encryptedSecret);
    } else {
      throw new AssertionError("OS downgrade not supported. KeyStore sealed data exists on platform < M!");
    }
  }

  private static byte[] createAndStoreSecret(@NonNull Context context) {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(secret);
    MessagingModuleConfiguration.getShared().getPrefs().setLogEncryptedSecret(encryptedSecret.serialize());

    return secret;
  }
}
