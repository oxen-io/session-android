package org.thoughtcrime.securesms.crypto;


import static org.session.libsignal.utilities.Util.SECURE_RANDOM;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;

import org.session.libsession.utilities.TextSecurePreferences;

import java.io.IOException;

public class DatabaseSecretProvider {

  @SuppressWarnings("unused")
  private static final String TAG = DatabaseSecretProvider.class.getSimpleName();

  private final Context context;

  public DatabaseSecretProvider(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  public DatabaseSecret getOrCreateDatabaseSecret() {
    String unencryptedSecret = TextSecurePreferences.getDatabaseUnencryptedSecret(context);
    String encryptedSecret   = TextSecurePreferences.getDatabaseEncryptedSecret(context);

    if      (unencryptedSecret != null) return getUnencryptedDatabaseSecret(context, unencryptedSecret);
    else if (encryptedSecret != null)   return getEncryptedDatabaseSecret(encryptedSecret);
    else                                return createAndStoreDatabaseSecret(context);
  }

  private DatabaseSecret getUnencryptedDatabaseSecret(@NonNull Context context, @NonNull String unencryptedSecret)
  {
    try {
      DatabaseSecret databaseSecret = new DatabaseSecret(unencryptedSecret);

      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());

      TextSecurePreferences.setDatabaseEncryptedSecret(context, encryptedSecret.serialize());
      TextSecurePreferences.setDatabaseUnencryptedSecret(context, null);

      return databaseSecret;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private DatabaseSecret getEncryptedDatabaseSecret(@NonNull String serializedEncryptedSecret) {
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
    return new DatabaseSecret(KeyStoreHelper.unseal(encryptedSecret));
  }

  private DatabaseSecret createAndStoreDatabaseSecret(@NonNull Context context) {
    byte[]       secret = new byte[32];
    SECURE_RANDOM.nextBytes(secret);

    DatabaseSecret databaseSecret = new DatabaseSecret(secret);

    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());
    TextSecurePreferences.setDatabaseEncryptedSecret(context, encryptedSecret.serialize());

    return databaseSecret;
  }
}
