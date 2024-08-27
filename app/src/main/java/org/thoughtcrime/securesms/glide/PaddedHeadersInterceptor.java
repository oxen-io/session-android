package org.thoughtcrime.securesms.glide;

import static org.session.libsignal.utilities.Util.SECURE_RANDOM;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An interceptor that adds a header with a random amount of bytes to disguise header length.
 */
public class PaddedHeadersInterceptor implements Interceptor {

  private static final String PADDING_HEADER   = "X-SignalPadding";
  private static final int    MIN_RANDOM_BYTES = 1;
  private static final int    MAX_RANDOM_BYTES = 64;

  @Override
  public @NonNull Response intercept(@NonNull Chain chain) throws IOException {
    Request padded = chain.request().newBuilder()
                                    .headers(getPaddedHeaders(chain.request().headers()))
                                    .build();

    return chain.proceed(padded);
  }

  private @NonNull Headers getPaddedHeaders(@NonNull Headers headers) {
    return headers.newBuilder()
                  .add(PADDING_HEADER, getRandomString(MIN_RANDOM_BYTES, MAX_RANDOM_BYTES))
                  .build();
  }

  private static @NonNull String getRandomString(int minLength, int maxLength) {
    char[] buffer = new char[SECURE_RANDOM.nextInt(maxLength - minLength) + minLength];

    for (int i = 0 ; i  < buffer.length; i++) {
      buffer[i] = (char) (SECURE_RANDOM.nextInt(74) + 48); // Random char from 0-Z
    }

    return new String(buffer);
  }
}
