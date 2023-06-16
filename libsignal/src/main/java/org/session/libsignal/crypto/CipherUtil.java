package org.session.libsignal.crypto;

public class CipherUtil {
    // Cipher operations are not thread-safe so we synchronize over them through doFinal to
    // prevent crashes with quickly repeated encrypt/decrypt operations
    // https://github.com/mozilla-mobile/android-components/issues/5342
    public static final Object CIPHER_LOCK = new Object();
}
