/*
 * Copyright (C) 2017 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.video;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.LegacyPlayerControlView;
import androidx.media3.ui.PlayerView;


import org.session.libsession.utilities.ViewUtil;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.attachments.AttachmentServer;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.VideoSlide;

import java.io.IOException;

import network.loki.messenger.R;

@UnstableApi
public class VideoPlayer extends FrameLayout {

  private static final String TAG = VideoPlayer.class.getSimpleName();

  @Nullable private final VideoView           videoView;
  @Nullable private final PlayerView exoView;

  @Nullable private ExoPlayer exoPlayer;
  @Nullable private LegacyPlayerControlView exoControls;
  @Nullable private       AttachmentServer    attachmentServer;
  @Nullable private       Window              window;

  public VideoPlayer(Context context) {
    this(context, null);
  }

  public VideoPlayer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.video_player, this);

    this.exoView   = ViewUtil.findById(this, R.id.video_view);
    this.videoView = null;
    this.exoControls = new LegacyPlayerControlView(getContext());
    this.exoControls.setShowTimeoutMs(-1);
  }

  public void setVideoSource(@NonNull VideoSlide videoSource, boolean autoplay)
      throws IOException
  {
    setExoViewSource(videoSource, autoplay);
  }

  public void pause() {
    if (this.attachmentServer != null && this.videoView != null) {
      this.videoView.stopPlayback();
    } else if (this.exoPlayer != null) {
      this.exoPlayer.setPlayWhenReady(false);
    }
  }

  public void hideControls() {
    if (this.exoView != null) {
      this.exoView.hideController();
    }
  }

  public @Nullable View getControlView() {
    if (this.exoControls != null) {
      return this.exoControls;
    }
    return null;
  }

  public void cleanup() {
    if (this.attachmentServer != null) {
      this.attachmentServer.stop();
    }

    if (this.exoPlayer != null) {
      this.exoPlayer.release();
    }
  }

  public void setWindow(@Nullable Window window) {
    this.window = window;
  }

  private void setExoViewSource(@NonNull VideoSlide videoSource, boolean autoplay)
      throws IOException
  {
    exoPlayer = new ExoPlayer.Builder(getContext()).build();
    exoPlayer.addListener(new ExoPlayerListener(window));
    exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true);
    //noinspection ConstantConditions
    exoView.setPlayer(exoPlayer);
    //noinspection ConstantConditions
    exoControls.setPlayer(exoPlayer);

    if(videoSource.getUri() != null){
      MediaItem mediaItem = MediaItem.fromUri(videoSource.getUri());
      exoPlayer.setMediaItem(mediaItem);
    }

    exoPlayer.prepare();
    exoPlayer.setPlayWhenReady(autoplay);
  }

  private void setVideoViewSource(@NonNull VideoSlide videoSource, boolean autoplay)
    throws IOException
  {
    if (this.attachmentServer != null) {
      this.attachmentServer.stop();
    }

    if (videoSource.getUri() != null && PartAuthority.isLocalUri(videoSource.getUri())) {
      Log.i(TAG, "Starting video attachment server for part provider Uri...");
      this.attachmentServer = new AttachmentServer(getContext(), videoSource.asAttachment());
      this.attachmentServer.start();

      //noinspection ConstantConditions
      this.videoView.setVideoURI(this.attachmentServer.getUri());
    } else if (videoSource.getUri() != null) {
      Log.i(TAG, "Playing video directly from non-local Uri...");
      //noinspection ConstantConditions
      this.videoView.setVideoURI(videoSource.getUri());
    } else {
      Toast.makeText(getContext(), getContext().getString(R.string.VideoPlayer_error_playing_video), Toast.LENGTH_LONG).show();
      return;
    }

    if (autoplay) this.videoView.start();
  }

  private static class ExoPlayerListener implements Player.Listener {
    private final Window window;

    ExoPlayerListener(Window window) {
      this.window = window;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      switch(playbackState) {
        case Player.STATE_IDLE:
        case Player.STATE_BUFFERING:
        case Player.STATE_ENDED:
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          break;
        case Player.STATE_READY:
          if (playWhenReady) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          }
          break;
        default:
          break;
      }
    }
  }
}
