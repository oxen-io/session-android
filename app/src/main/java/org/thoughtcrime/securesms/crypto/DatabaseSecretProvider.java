package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.session.libsession.utilities.TextSecurePreferences;

import java.io.IOException;
import java.security.SecureRandom;

public class DatabaseSecretProvider {

  @SuppressWarnings("unused")
  private static final String TAG = DatabaseSecretProvider.class.getSimpleName();

  private final TextSecurePreferences prefs;

  public DatabaseSecretProvider(@NonNull TextSecurePreferences prefs) {
    this.prefs = prefs;
  }

  public DatabaseSecret getOrCreateDatabaseSecret() {
    String unencryptedSecret = prefs.getDatabaseUnencryptedSecret();
    String encryptedSecret   = prefs.getDatabaseEncryptedSecret();

    if      (unencryptedSecret != null) return getUnencryptedDatabaseSecret(unencryptedSecret);
    else if (encryptedSecret != null)   return getEncryptedDatabaseSecret(encryptedSecret);
    else                                return createAndStoreDatabaseSecret();
  }

  private DatabaseSecret getUnencryptedDatabaseSecret(@NonNull String unencryptedSecret)
  {
    try {
      DatabaseSecret databaseSecret = new DatabaseSecret(unencryptedSecret);

      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());

      prefs.setDatabaseEncryptedSecret(encryptedSecret.serialize());
      prefs.setDatabaseUnencryptedSecret(null);

      return databaseSecret;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private DatabaseSecret getEncryptedDatabaseSecret(@NonNull String serializedEncryptedSecret) {
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
    return new DatabaseSecret(KeyStoreHelper.unseal(encryptedSecret));
  }

  private DatabaseSecret createAndStoreDatabaseSecret() {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    DatabaseSecret databaseSecret = new DatabaseSecret(secret);

    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());
    prefs.setDatabaseEncryptedSecret(encryptedSecret.serialize());

    return databaseSecret;
  }
}
