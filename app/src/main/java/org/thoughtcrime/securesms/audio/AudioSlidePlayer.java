package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import java.io.IOException;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.NotNull;
import org.session.libsession.utilities.ServiceUtil;
import org.session.libsession.utilities.Util;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.guava.Optional;
import org.thoughtcrime.securesms.attachments.AttachmentServer;
import org.thoughtcrime.securesms.mms.AudioSlide;

public class AudioSlidePlayer implements SensorEventListener {

  private static final String TAG = AudioSlidePlayer.class.getSimpleName();

  private static @NonNull Optional<AudioSlidePlayer> playing = Optional.absent();

  private final @NonNull  Context           context;
  private final @NonNull  AudioSlide        slide;
  private final @NonNull  Handler           progressEventHandler;
  private final @NonNull  AudioManager      audioManager;
  private final @NonNull  SensorManager     sensorManager;
  private final Sensor            proximitySensor;
  private final @Nullable WakeLock          wakeLock;

  private @NonNull  WeakReference<Listener> listener;
  private @Nullable ExoPlayer mediaPlayer;
  private @Nullable AttachmentServer        audioAttachmentServer;
  private           long                    startTime;

  public synchronized static AudioSlidePlayer createFor(@NonNull Context context,
                                                        @NonNull AudioSlide slide,
                                                        @NonNull Listener listener)
  {
    if (playing.isPresent() && playing.get().getAudioSlide().equals(slide)) {
      playing.get().setListener(listener);
      return playing.get();
    } else {
      return new AudioSlidePlayer(context, slide, listener);
    }
  }

  @Nullable
  public synchronized static AudioSlidePlayer getInstance() {
    return playing.orNull();
  }

  private AudioSlidePlayer(@NonNull Context context,
                           @NonNull AudioSlide slide,
                           @NonNull Listener listener)
  {
    this.context              = context;
    this.slide                = slide;
    this.listener             = new WeakReference<>(listener);
    this.progressEventHandler = new ProgressEventHandler(this);
    this.audioManager         = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    this.sensorManager        = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
    this.proximitySensor      = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

    this.wakeLock = ServiceUtil.getPowerManager(context).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
  }

  public void play(final double progress) throws IOException {
    play(progress, false);
  }

  @OptIn(markerClass = UnstableApi.class)
  private void play(final double progress, boolean earpiece) throws IOException {
    if (this.mediaPlayer != null) { stop(); }

    this.mediaPlayer = new ExoPlayer.Builder(context).build();
    this.audioAttachmentServer = new AttachmentServer(context, slide.asAttachment());
    this.startTime             = System.currentTimeMillis();

    audioAttachmentServer.start();

    MediaItem mediaItem = MediaItem.fromUri(audioAttachmentServer.getUri());
    mediaPlayer.setMediaItem(mediaItem);

    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                                                      .setContentType(earpiece ? C.AUDIO_CONTENT_TYPE_SPEECH : C.AUDIO_CONTENT_TYPE_MUSIC)
                                                      .setUsage(earpiece ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
                                                      .build(),
            !earpiece);
    mediaPlayer.addListener(new Player.Listener() {

      boolean started = false;

      @Override
      public void onPlaybackStateChanged(int playbackState) {
        Log.d(TAG, "onPlaybackStateChanged(" + playbackState + ")");
        switch (playbackState) {
          case Player.STATE_READY:
            Log.i(TAG, "onPrepared() " + mediaPlayer.getBufferedPercentage() + "% buffered");
            synchronized (AudioSlidePlayer.this) {
              if (mediaPlayer == null) return;

              if (started) {
                Log.d(TAG, "Already started. Ignoring.");
                return;
              }

              started = true;

              if (progress > 0) {
                mediaPlayer.seekTo((long) (mediaPlayer.getDuration() * progress));
              }

              if(proximitySensor != null) {
                sensorManager.registerListener(AudioSlidePlayer.this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
              }

              setPlaying(AudioSlidePlayer.this);
            }

            notifyOnStart();
            progressEventHandler.sendEmptyMessage(0);
            break;

          case Player.STATE_ENDED:
            Log.i(TAG, "onComplete");

            long millis = mediaPlayer.getDuration();

            synchronized (AudioSlidePlayer.this) {
              mediaPlayer.release();
              mediaPlayer = null;

              if (audioAttachmentServer != null) {
                audioAttachmentServer.stop();
                audioAttachmentServer = null;
              }

              sensorManager.unregisterListener(AudioSlidePlayer.this);

              if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
              }
            }

            notifyOnProgress(1.0, millis);
            notifyOnStop();
            progressEventHandler.removeMessages(0);
        }
      }

      @Override
      public void onPlayerError(PlaybackException error) {
        Log.w(TAG, "MediaPlayer Error: " + error);

        synchronized (AudioSlidePlayer.this) {
          mediaPlayer = null;

          if (audioAttachmentServer != null) {
            audioAttachmentServer.stop();
            audioAttachmentServer = null;
          }

          sensorManager.unregisterListener(AudioSlidePlayer.this);

          if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
          }
        }

        notifyOnStop();
        progressEventHandler.removeMessages(0);
      }
    });

    mediaPlayer.prepare();
    mediaPlayer.setPlayWhenReady(true);
  }

  public synchronized void stop() {
    Log.i(TAG, "Stop called!");

    removePlaying(this);

    if (this.mediaPlayer != null) {
      this.mediaPlayer.stop();
      this.mediaPlayer.release();
    }

    if (this.audioAttachmentServer != null) { this.audioAttachmentServer.stop(); }

    sensorManager.unregisterListener(AudioSlidePlayer.this);

    this.mediaPlayer           = null;
    this.audioAttachmentServer = null;
  }

  public synchronized static void stopAll() {
    if (playing.isPresent()) { playing.get().stop(); }
  }

  public synchronized boolean isReady() {
    if (mediaPlayer == null) return false;

    return mediaPlayer.getPlaybackState() == Player.STATE_READY && mediaPlayer.getPlayWhenReady();
  }

  public synchronized void seekTo(double progress) throws IOException {
    if (mediaPlayer == null || !isReady()) {
      play(progress);
    } else {
      mediaPlayer.seekTo((long) (mediaPlayer.getDuration() * progress));
    }
  }

  public void setListener(@NonNull Listener listener) {
    this.listener = new WeakReference<>(listener);

    if (this.mediaPlayer != null && this.mediaPlayer.getPlaybackState() == Player.STATE_READY) {
      notifyOnStart();
    }
  }

  public @NonNull AudioSlide getAudioSlide() {
    return slide;
  }

  public Long getDuration() {
    if (mediaPlayer == null) { return 0L; }
    return mediaPlayer.getDuration();
  }

  public Double getProgress() {
    if (mediaPlayer == null) { return 0.0; }
    return (double) mediaPlayer.getCurrentPosition() / (double) mediaPlayer.getDuration();
  }

  private Pair<Double, Integer> getProgressTuple() {
    if (mediaPlayer == null || mediaPlayer.getCurrentPosition() <= 0 || mediaPlayer.getDuration() <= 0) {
      return new Pair<>(0D, 0);
    } else {
      return new Pair<>((double) mediaPlayer.getCurrentPosition() / (double) mediaPlayer.getDuration(),
                        (int) mediaPlayer.getCurrentPosition());
    }
  }

  public float getPlaybackSpeed() {
    if (mediaPlayer == null) { return 1.0f; }
    return mediaPlayer.getPlaybackParameters().speed;
  }

  public void setPlaybackSpeed(float speed) {
    if (mediaPlayer == null) { return; }
    mediaPlayer.setPlaybackParameters(new PlaybackParameters(speed));
  }

  private void notifyOnStart() {
    Util.runOnMain(() -> getListener().onPlayerStart(AudioSlidePlayer.this));
  }

  private void notifyOnStop() {
    Util.runOnMain(() -> getListener().onPlayerStop(AudioSlidePlayer.this));
  }

  private void notifyOnProgress(final double progress, final long millis) {
    Util.runOnMain(() -> getListener().onPlayerProgress(AudioSlidePlayer.this, progress, millis));
  }

  private @NonNull Listener getListener() {
    Listener listener = this.listener.get();

    if (listener != null) return listener;
    else                  return new Listener() {
      @Override
      public void onPlayerStart(@NotNull AudioSlidePlayer player) { }
      @Override
      public void onPlayerStop(@NotNull AudioSlidePlayer player) { }
      @Override
      public void onPlayerProgress(@NotNull AudioSlidePlayer player, double progress, long millis) { }
    };
  }

  private synchronized static void setPlaying(@NonNull AudioSlidePlayer player) {
    if (playing.isPresent() && playing.get() != player) {
      playing.get().notifyOnStop();
      playing.get().stop();
    }

    playing = Optional.of(player);
  }

  private synchronized static void removePlaying(@NonNull AudioSlidePlayer player) {
    if (playing.isPresent() && playing.get() == player) {
      playing = Optional.absent();
    }
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() != Sensor.TYPE_PROXIMITY) return;
    if (mediaPlayer == null || mediaPlayer.getPlaybackState() != Player.STATE_READY) return;

    int streamType;

    if (
            proximitySensor != null &&
            event.values[0] < 5f &&
            event.values[0] != proximitySensor.getMaximumRange()
    ) {
      streamType = C.AUDIO_CONTENT_TYPE_SPEECH;
    } else {
      streamType = C.AUDIO_CONTENT_TYPE_MUSIC;
    }

    if (streamType == C.AUDIO_CONTENT_TYPE_SPEECH &&
        mediaPlayer.getAudioAttributes().contentType != streamType &&
        !audioManager.isWiredHeadsetOn())
    {
      double position = mediaPlayer.getCurrentPosition();
      double duration = mediaPlayer.getDuration();
      double progress = position / duration;

      if (wakeLock != null) wakeLock.acquire();
      stop();
      try {
        play(progress, true);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    } else if (streamType == C.AUDIO_CONTENT_TYPE_MUSIC &&
               mediaPlayer.getAudioAttributes().contentType != streamType &&
               System.currentTimeMillis() - startTime > 500)
    {
      if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
      stop();
      notifyOnStop();
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) { /* Do nothing */ }


  public interface Listener {
    void onPlayerStart(@NonNull AudioSlidePlayer player);
    void onPlayerStop(@NonNull AudioSlidePlayer player);
    void onPlayerProgress(@NonNull AudioSlidePlayer player, double progress, long millis);
  }

  private static class ProgressEventHandler extends Handler {

    private final WeakReference<AudioSlidePlayer> playerReference;

    private ProgressEventHandler(@NonNull AudioSlidePlayer player) {
      this.playerReference = new WeakReference<>(player);
    }

    @Override
    public void handleMessage(Message msg) {
      AudioSlidePlayer player = playerReference.get();

      if (player == null || player.mediaPlayer == null || !isPlayerActive(player.mediaPlayer)) {
        return;
      }

      Pair<Double, Integer> progress = player.getProgressTuple();
      player.notifyOnProgress(progress.first, progress.second);
      sendEmptyMessageDelayed(0, 50);
    }

    private boolean isPlayerActive(@NonNull ExoPlayer player) {
      return player.getPlaybackState() == Player.STATE_READY || player.getPlaybackState() == Player.STATE_BUFFERING;
    }
  }
}
