package org.thoughtcrime.securesms.crypto;


import androidx.annotation.NonNull;

import org.session.libsession.messaging.MessagingModuleConfiguration;

import java.security.SecureRandom;

/**
 * A provider that is responsible for creating or retrieving the AttachmentSecret model.
 *
 * On modern Android, the serialized secrets are themselves encrypted using a key that lives
 * in the system KeyStore, for whatever that is worth.
 */
public class AttachmentSecretProvider {

  private static AttachmentSecretProvider provider;

  public static synchronized AttachmentSecretProvider getInstance() {
    if (provider == null) provider = new AttachmentSecretProvider();
    return provider;
  }

  private AttachmentSecret attachmentSecret;

  public synchronized AttachmentSecret getOrCreateAttachmentSecret() {
    if (attachmentSecret != null) return attachmentSecret;

    String unencryptedSecret = MessagingModuleConfiguration.getShared().getPrefs().getAttachmentUnencryptedSecret();
    String encryptedSecret   = MessagingModuleConfiguration.getShared().getPrefs().getAttachmentEncryptedSecret();

    if      (unencryptedSecret != null) attachmentSecret = getUnencryptedAttachmentSecret(unencryptedSecret);
    else if (encryptedSecret != null)   attachmentSecret = getEncryptedAttachmentSecret(encryptedSecret);
    else                                attachmentSecret = createAndStoreAttachmentSecret();

    return attachmentSecret;
  }

  private AttachmentSecret getUnencryptedAttachmentSecret(@NonNull String unencryptedSecret) {
    AttachmentSecret attachmentSecret = AttachmentSecret.fromString(unencryptedSecret);

      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(attachmentSecret.serialize().getBytes());

      MessagingModuleConfiguration.getShared().getPrefs().setAttachmentEncryptedSecret(encryptedSecret.serialize());
      MessagingModuleConfiguration.getShared().getPrefs().setAttachmentUnencryptedSecret(null);

      return attachmentSecret;
  }

  private AttachmentSecret getEncryptedAttachmentSecret(@NonNull String serializedEncryptedSecret) {
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
    return AttachmentSecret.fromString(new String(KeyStoreHelper.unseal(encryptedSecret)));
  }

  private AttachmentSecret createAndStoreAttachmentSecret() {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    AttachmentSecret attachmentSecret = new AttachmentSecret(null, null, secret);
    storeAttachmentSecret(attachmentSecret);

    return attachmentSecret;
  }

  private void storeAttachmentSecret(@NonNull AttachmentSecret attachmentSecret) {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(attachmentSecret.serialize().getBytes());
      MessagingModuleConfiguration.getShared().getPrefs().setAttachmentEncryptedSecret(encryptedSecret.serialize());
  }
}
