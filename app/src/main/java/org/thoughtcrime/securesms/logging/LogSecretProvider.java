package org.thoughtcrime.securesms.logging;

import static org.session.libsignal.utilities.Util.SECURE_RANDOM;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.KeyStoreHelper;
import org.session.libsignal.utilities.Base64;
import org.session.libsession.utilities.TextSecurePreferences;

import java.io.IOException;

class LogSecretProvider {

  static byte[] getOrCreateAttachmentSecret(@NonNull Context context) {
    String unencryptedSecret = TextSecurePreferences.getLogUnencryptedSecret(context);
    String encryptedSecret   = TextSecurePreferences.getLogEncryptedSecret(context);

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
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(secret);
    return KeyStoreHelper.unseal(encryptedSecret);
  }

  private static byte[] createAndStoreSecret(@NonNull Context context) {
    byte[]       secret = new byte[32];
    SECURE_RANDOM.nextBytes(secret);

    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(secret);
    TextSecurePreferences.setLogEncryptedSecret(context, encryptedSecret.serialize());

    return secret;
  }
}
