/*
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.conversation.v2.utilities;

import static com.google.android.gms.common.util.CollectionUtils.listOf;
import static org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.squareup.phrase.Phrase;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import network.loki.messenger.R;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsignal.utilities.ListenableFuture;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.SettableFuture;
import org.session.libsignal.utilities.guava.Optional;
import org.thoughtcrime.securesms.giph.ui.GiphyActivity;
import org.thoughtcrime.securesms.mediasend.MediaSendActivity;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DocumentSlide;
import org.thoughtcrime.securesms.mms.GifSlide;
import com.bumptech.glide.RequestManager;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;

public class AttachmentManager {

  private final static String TAG = AttachmentManager.class.getSimpleName();

  // Max attachment size is 10MB, above which we display a warning toast rather than sending the msg
  private final long MAX_ATTACHMENTS_FILE_SIZE_BYTES = 10 * 1024 * 1024;

  private final @NonNull Context                    context;
  private final @NonNull AttachmentListener         attachmentListener;

  private @NonNull  List<Uri>       garbage = new LinkedList<>();
  private @NonNull  Optional<Slide> slide   = Optional.absent();
  private @Nullable Uri             captureUri;

  public AttachmentManager(@NonNull Activity activity, @NonNull AttachmentListener listener) {
    this.context            = activity;
    this.attachmentListener = listener;
  }

  public void clear() {
    markGarbage(getSlideUri());
    slide = Optional.absent();
    attachmentListener.onAttachmentChanged();
  }

  public void cleanup() {
    cleanup(captureUri);
    cleanup(getSlideUri());

    captureUri = null;
    slide      = Optional.absent();

    Iterator<Uri> iterator = garbage.listIterator();

    while (iterator.hasNext()) {
      cleanup(iterator.next());
      iterator.remove();
    }
  }

  private void cleanup(final @Nullable Uri uri) {
    if (uri != null && BlobProvider.isAuthority(uri)) {
      BlobProvider.getInstance().delete(context, uri);
    }
  }

  private void markGarbage(@Nullable Uri uri) {
    if (uri != null && BlobProvider.isAuthority(uri)) {
      Log.d(TAG, "Marking garbage that needs cleaning: " + uri);
      garbage.add(uri);
    }
  }

  private void setSlide(@NonNull Slide slide) {
    if (getSlideUri() != null) {
      cleanup(getSlideUri());
    }

    if (captureUri != null && !captureUri.equals(slide.getUri())) {
      cleanup(captureUri);
      captureUri = null;
    }

    this.slide = Optional.of(slide);
  }

  @SuppressLint("StaticFieldLeak")
  public ListenableFuture<Boolean> setMedia(@NonNull final RequestManager glideRequests,
                                            @NonNull final Uri uri,
                                            @NonNull final MediaType mediaType,
                                            @NonNull final MediaConstraints constraints,
                                                     final int width,
                                                     final int height)
  {
    final SettableFuture<Boolean> result = new SettableFuture<>();

    new AsyncTask<Void, Void, Slide>() {
      @Override
      protected void onPreExecute() {

      }

      @Override
      protected @Nullable Slide doInBackground(Void... params) {
        try {
          if (PartAuthority.isLocalUri(uri)) {
            return getManuallyCalculatedSlideInfo(uri, width, height);
          } else {
            Slide result = getContentResolverSlideInfo(uri, width, height);

            if (result == null) return getManuallyCalculatedSlideInfo(uri, width, height);
            else                return result;
          }
        } catch (IOException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      @Override
      protected void onPostExecute(@Nullable final Slide slide) {
        if (slide == null) {
          result.set(false);
        } else if (!areConstraintsSatisfied(context, slide, constraints)) {
          result.set(false);
        } else {
          setSlide(slide);
          result.set(true);
          attachmentListener.onAttachmentChanged();
        }
      }

      private @Nullable Slide getContentResolverSlideInfo(Uri uri, int width, int height) {
        Cursor cursor = null;
        long   start  = System.currentTimeMillis();

        try {
          cursor = context.getContentResolver().query(uri, null, null, null, null);

          if (cursor != null && cursor.moveToFirst()) {
            String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            long   fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
            String mimeType = context.getContentResolver().getType(uri);

            if (width == 0 || height == 0) {
              Pair<Integer, Integer> dimens = MediaUtil.getDimensions(context, mimeType, uri);
              width  = dimens.first;
              height = dimens.second;
            }

            Log.d(TAG, "remote slide with size " + fileSize + " took " + (System.currentTimeMillis() - start) + "ms");
            return mediaType.createSlide(context, uri, fileName, mimeType, fileSize, width, height);
          }
        } finally {
          if (cursor != null) cursor.close();
        }

        return null;
      }

      private @NonNull Slide getManuallyCalculatedSlideInfo(Uri uri, int width, int height) throws IOException {
        long start      = System.currentTimeMillis();
        Long mediaSize  = null;
        String fileName = null;
        String mimeType = null;

        if (PartAuthority.isLocalUri(uri)) {
          mediaSize = PartAuthority.getAttachmentSize(context, uri);
          fileName  = PartAuthority.getAttachmentFileName(context, uri);
          mimeType  = PartAuthority.getAttachmentContentType(context, uri);
        }

        if (mediaSize == null) {
          mediaSize = MediaUtil.getMediaSize(context, uri);
        }

        if (mimeType == null) {
          mimeType = MediaUtil.getMimeType(context, uri);
        }

        if (width == 0 || height == 0) {
          Pair<Integer, Integer> dimens = MediaUtil.getDimensions(context, mimeType, uri);
          width  = dimens.first;
          height = dimens.second;
        }

        Log.d(TAG, "local slide with size " + mediaSize + " took " + (System.currentTimeMillis() - start) + "ms");
        return mediaType.createSlide(context, uri, fileName, mimeType, mediaSize, width, height);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    return result;
  }

  public @NonNull
  SlideDeck buildSlideDeck() {
    SlideDeck deck = new SlideDeck();
    if (slide.isPresent()) deck.addSlide(slide.get());
    return deck;
  }

  public static void selectDocument(Activity activity, int requestCode) {
    Permissions.PermissionsBuilder builder = Permissions.with(activity);
    Context c = activity.getApplicationContext();

    // The READ_EXTERNAL_STORAGE permission is deprecated (and will AUTO-FAIL if requested!) on
    // Android 13 and above (API 33 - 'Tiramisu') we must ask for READ_MEDIA_VIDEO/IMAGES/AUDIO instead.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      builder = builder.request(Manifest.permission.READ_MEDIA_VIDEO)
                       .request(Manifest.permission.READ_MEDIA_IMAGES)
                       .request(Manifest.permission.READ_MEDIA_AUDIO)
                       .withRationaleDialog(
                               Phrase.from(c, R.string.permissionsMusicAudio)
                                       .put(APP_NAME_KEY, c.getString(R.string.app_name)).format().toString()
                       )
                       .withPermanentDenialDialog(
                            Phrase.from(c, R.string.permissionMusicAudioDenied)
                                    .put(APP_NAME_KEY, c.getString(R.string.app_name))
                                    .format().toString()
                        );
    } else {
      builder = builder.request(Manifest.permission.READ_EXTERNAL_STORAGE)
              .withPermanentDenialDialog(
                      Phrase.from(c, R.string.permissionsStorageDeniedLegacy)
                              .put(APP_NAME_KEY, c.getString(R.string.app_name))
                              .format().toString()
              );
    }

    builder.onAllGranted(() -> selectMediaType(activity, "*/*", null, requestCode)) // Note: We can use startActivityForResult w/ the ACTION_OPEN_DOCUMENT or ACTION_OPEN_DOCUMENT_TREE intent if we need to modernise this.
            .execute();
  }

  public static void selectGallery(Activity activity, int requestCode, @NonNull Recipient recipient, @NonNull String body) {

    Context c = activity.getApplicationContext();

    Permissions.PermissionsBuilder builder = Permissions.with(activity);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      builder = builder.request(Manifest.permission.READ_MEDIA_VIDEO)
              .request(Manifest.permission.READ_MEDIA_IMAGES)
              .withPermanentDenialDialog(
                      Phrase.from(c, R.string.permissionsStorageDenied)
                              .put(APP_NAME_KEY, c.getString(R.string.app_name))
                              .format().toString()
              );
    } else {
      builder = builder.request(Manifest.permission.READ_EXTERNAL_STORAGE)
              .withPermanentDenialDialog(
                      Phrase.from(c, R.string.permissionsStorageDeniedLegacy)
                              .put(APP_NAME_KEY, c.getString(R.string.app_name))
                              .format().toString()
              );
    }
    builder.onAllGranted(() -> activity.startActivityForResult(MediaSendActivity.buildGalleryIntent(activity, recipient, body), requestCode))
      .execute();
  }

  public static void selectAudio(Activity activity, int requestCode) {
    selectMediaType(activity, "audio/*", null, requestCode);
  }

  public static void selectGif(Activity activity, int requestCode) {
    Intent intent = new Intent(activity, GiphyActivity.class);
    intent.putExtra(GiphyActivity.EXTRA_IS_MMS, false);
    activity.startActivityForResult(intent, requestCode);
  }

  private @Nullable Uri getSlideUri() {
    return slide.isPresent() ? slide.get().getUri() : null;
  }

  public @Nullable Uri getCaptureUri() {
    return captureUri;
  }

  public void capturePhoto(Activity activity, int requestCode, Recipient recipient) {

    String cameraPermissionDeniedTxt = Phrase.from(context, R.string.permissionsCameraDenied)
            .put(APP_NAME_KEY, context.getString(R.string.app_name))
            .format().toString();

    Permissions.with(activity)
        .request(Manifest.permission.CAMERA)
        .withPermanentDenialDialog(cameraPermissionDeniedTxt)
        .onAllGranted(() -> {
          Intent captureIntent = MediaSendActivity.buildCameraIntent(activity, recipient);
          if (captureIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivityForResult(captureIntent, requestCode);
          }
        })
        .execute();
  }

  private static void selectMediaType(Activity activity, @NonNull String type, @Nullable String[] extraMimeType, int requestCode) {
    final Intent intent = new Intent();
    intent.setType(type);

    if (extraMimeType != null) {
      intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeType);
    }

    intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
    try {
      activity.startActivityForResult(intent, requestCode);
      return;
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, "couldn't complete ACTION_OPEN_DOCUMENT, no activity found. falling back.");
    }

    intent.setAction(Intent.ACTION_GET_CONTENT);

    try {
      activity.startActivityForResult(intent, requestCode);
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, "couldn't complete ACTION_GET_CONTENT intent, no activity found. falling back.");
      Toast.makeText(activity, R.string.attachmentsErrorNoApp, Toast.LENGTH_LONG).show();
    }
  }

  private boolean areConstraintsSatisfied(final @NonNull  Context context,
                                          final @Nullable Slide slide,
                                          final @NonNull  MediaConstraints constraints)
  {
    // Null attachment? Not satisfied.
    if (slide == null) return false;

    // Attachments are excessively large? Not satisfied.
    // Note: This file size test must come BEFORE the `constraints.isSatisfied` check below because
    // it is a more specific type of check.
    if (slide.asAttachment().getSize() > MAX_ATTACHMENTS_FILE_SIZE_BYTES) {
      Toast.makeText(context, R.string.attachmentsErrorSize, Toast.LENGTH_SHORT).show();
      return false;
    }

    // Otherwise we return whether our constraints are satisfied OR if we can resize the attachment
    // (in the case of one or more images) - either one will be acceptable, but if both aren't then
    // we fail the constraint test.
    return constraints.isSatisfied(context, slide.asAttachment()) || constraints.canResize(slide.asAttachment());
  }

  public interface AttachmentListener {
    void onAttachmentChanged();
  }

  public enum MediaType {
    IMAGE, GIF, AUDIO, VIDEO, DOCUMENT, VCARD;

    public @NonNull Slide createSlide(@NonNull  Context context,
                                      @NonNull  Uri     uri,
                                      @Nullable String fileName,
                                      @Nullable String mimeType,
                                                long    dataSize,
                                                int     width,
                                                int     height)
    {
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }

      switch (this) {
      case IMAGE:    return new ImageSlide(context, uri, dataSize, width, height);
      case GIF:      return new GifSlide(context, uri, dataSize, width, height);
      case AUDIO:    return new AudioSlide(context, uri, dataSize, false);
      case VIDEO:    return new VideoSlide(context, uri, dataSize);
      case VCARD:
      case DOCUMENT: return new DocumentSlide(context, uri, mimeType, dataSize, fileName);
      default:       throw  new AssertionError("unrecognized enum");
      }
    }

    public static @Nullable MediaType from(final @Nullable String mimeType) {
      if (TextUtils.isEmpty(mimeType))     return null;
      if (MediaUtil.isGif(mimeType))       return GIF;
      if (MediaUtil.isImageType(mimeType)) return IMAGE;
      if (MediaUtil.isAudioType(mimeType)) return AUDIO;
      if (MediaUtil.isVideoType(mimeType)) return VIDEO;
      if (MediaUtil.isVcard(mimeType))     return VCARD;

      return DOCUMENT;
    }
  }
}
