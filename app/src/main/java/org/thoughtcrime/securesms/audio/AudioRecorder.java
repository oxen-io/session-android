package org.thoughtcrime.securesms.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;

import org.session.libsession.utilities.MediaTypes;
import org.session.libsignal.utilities.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBar;
import org.thoughtcrime.securesms.conversation.v2.input_bar.VoiceRecorderState;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;

import org.session.libsignal.utilities.ThreadUtils;
import org.session.libsession.utilities.Util;
import org.session.libsignal.utilities.ListenableFuture;
import org.session.libsignal.utilities.SettableFuture;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class AudioRecorder {

  private static final String TAG = AudioRecorder.class.getSimpleName();

  private static final ExecutorService executor = ThreadUtils.newDynamicSingleThreadedExecutor();

  private final Context context;

  private AudioCodec audioCodec;
  private Uri        captureUri;

  public AudioRecorder(@NonNull Context context) {
    this.context = context;
  }

  public void startRecording(InputBar inputBar) {
    Log.i(TAG, "startRecording()");

    executor.execute(() -> {
      Log.i(TAG, "Running startRecording() + " + Thread.currentThread().getId());
      try {
        if (audioCodec != null) {
          Log.e(TAG, "Trying to start recording while another recording is in progress, exiting...");
          return;
        }

        ParcelFileDescriptor fds[] = ParcelFileDescriptor.createPipe();

        captureUri = BlobProvider.getInstance()
                                 .forData(new ParcelFileDescriptor.AutoCloseInputStream(fds[0]), 0)
                                 .withMimeType(MediaTypes.AUDIO_AAC)
                                 .createForSingleSessionOnDisk(context, e -> Log.w(TAG, "Error during recording", e));

        audioCodec = new AudioCodec();
        audioCodec.start(new ParcelFileDescriptor.AutoCloseOutputStream(fds[1]));

        // If we just tap the record audio button then by the time we actually finish setting up and
        // get here the recording has been cancelled and the voice recorder state is Idle! As such
        // we'll only tick the recorder state over to Recording if we were still in the
        // SettingUpToRecord state when we got here (i.e., the record voice message button is still
        // held or locked to keep recording without being held).
        if (inputBar.getVoiceRecorderState() == VoiceRecorderState.SettingUpToRecord) {
          inputBar.setVoiceRecorderState(VoiceRecorderState.Recording);
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    });
  }

  public @NonNull ListenableFuture<Pair<Uri, Long>> stopRecording() {
    Log.i(TAG, "stopRecording()");

    final SettableFuture<Pair<Uri, Long>> future = new SettableFuture<>();

    executor.execute(() -> {
      if (audioCodec == null) {
        sendToFuture(future, new IOException("MediaRecorder was never initialized successfully!"));
        return;
      }

      audioCodec.stop();

      try {
        long size = MediaUtil.getMediaSize(context, captureUri);
        sendToFuture(future, new Pair<>(captureUri, size));
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        sendToFuture(future, ioe);
      }

      audioCodec = null;
      captureUri = null;
    });

    return future;
  }

  private <T> void sendToFuture(final SettableFuture<T> future, final Exception exception) {
    Util.runOnMain(() -> future.setException(exception));
  }

  private <T> void sendToFuture(final SettableFuture<T> future, final T result) {
    Util.runOnMain(() -> future.set(result));
  }
}
