package org.thoughtcrime.securesms.mediasend;

import static org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.squareup.phrase.Phrase;

import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.MediaTypes;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.concurrent.SimpleTask;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.guava.Optional;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import network.loki.messenger.R;

/**
 * Encompasses the entire flow of sending media, starting from the selection process to the actual
 * captioning and editing of the content.
 *
 * This activity is intended to be launched via {@link #startActivityForResult(Intent, int)}.
 * It will return the {@link Media} that the user decided to send.
 */
public class MediaSendActivity extends PassphraseRequiredActionBarActivity implements MediaPickerFolderFragment.Controller,
                                                                                      MediaPickerItemFragment.Controller,
                                                                                      MediaSendFragment.Controller,
                                                                                      ImageEditorFragment.Controller,
                                                                                      Camera1Fragment.Controller
{
  private static final String TAG = MediaSendActivity.class.getSimpleName();

  public static final String EXTRA_MEDIA     = "media";
  public static final String EXTRA_MESSAGE   = "message";


  private static final String KEY_ADDRESS   = "address";
  private static final String KEY_BODY      = "body";
  private static final String KEY_MEDIA     = "media";
  private static final String KEY_IS_CAMERA = "is_camera";

  private static final String TAG_FOLDER_PICKER = "folder_picker";
  private static final String TAG_ITEM_PICKER   = "item_picker";
  private static final String TAG_SEND          = "send";
  private static final String TAG_CAMERA        = "camera";


  private Recipient          recipient;
  private MediaSendViewModel viewModel;

  private View     countButton;
  private TextView countButtonText;
  private View     cameraButton;

  /**
   * Get an intent to launch the media send flow starting with the picker.
   */
  public static Intent buildGalleryIntent(@NonNull Context context, @NonNull Recipient recipient, @NonNull String body) {
    Intent intent = new Intent(context, MediaSendActivity.class);
    intent.putExtra(KEY_ADDRESS, recipient.getAddress().serialize());
    intent.putExtra(KEY_BODY, body);
    return intent;
  }

  /**
   * Get an intent to launch the media send flow starting with the camera.
   */
  public static Intent buildCameraIntent(@NonNull Context context, @NonNull Recipient recipient) {
    Intent intent = buildGalleryIntent(context, recipient, "");
    intent.putExtra(KEY_IS_CAMERA, true);
    return intent;
  }

  /**
   * Get an intent to launch the media send flow with a specific list of media. Will jump right to
   * the editor screen.
   */
  public static Intent buildEditorIntent(@NonNull Context context,
                                         @NonNull List<Media> media,
                                         @NonNull Recipient recipient,
                                         @NonNull String body)
  {
    Intent intent = buildGalleryIntent(context, recipient, body);
    intent.putParcelableArrayListExtra(KEY_MEDIA, new ArrayList<>(media));
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    setContentView(R.layout.mediasend_activity);
    setResult(RESULT_CANCELED);

    if (savedInstanceState != null) {
      return;
    }

    countButton     = findViewById(R.id.mediasend_count_button);
    countButtonText = findViewById(R.id.mediasend_count_button_text);
    cameraButton    = findViewById(R.id.mediasend_camera_button);

    viewModel = new ViewModelProvider(this, new MediaSendViewModel.Factory(getApplication(), new MediaRepository())).get(MediaSendViewModel.class);
    recipient = Recipient.from(this, Address.fromSerialized(getIntent().getStringExtra(KEY_ADDRESS)), true);

    viewModel.onBodyChanged(getIntent().getStringExtra(KEY_BODY));

    List<Media> media    = getIntent().getParcelableArrayListExtra(KEY_MEDIA);
    boolean     isCamera = getIntent().getBooleanExtra(KEY_IS_CAMERA, false);

    if (isCamera) {
      Fragment fragment = Camera1Fragment.newInstance();
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_CAMERA)
                                 .commit();

    } else if (!Util.isEmpty(media)) {
      viewModel.onSelectedMediaChanged(this, media);

      Fragment fragment = MediaSendFragment.newInstance(recipient);
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_SEND)
                                 .commit();
    } else {
      MediaPickerFolderFragment fragment = MediaPickerFolderFragment.newInstance(recipient);
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.mediasend_fragment_container, fragment, TAG_FOLDER_PICKER)
                                 .commit();
    }

    initializeCountButtonObserver();
    initializeCameraButtonObserver();
    initializeErrorObserver();

    cameraButton.setOnClickListener(v -> {
      int maxSelection = viewModel.getMaxSelection();

      if (viewModel.getSelectedMedia().getValue() != null && viewModel.getSelectedMedia().getValue().size() >= maxSelection) {
        Toast.makeText(this, getString(R.string.attachmentsErrorNumber), Toast.LENGTH_SHORT).show();
      } else {
        navigateToCamera();
      }
    });
  }

  @Override
  public void onBackPressed() {
    MediaSendFragment sendFragment = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
    if (sendFragment == null || !sendFragment.isVisible() || !sendFragment.handleBackPress()) {
      super.onBackPressed();

      if (getIntent().getBooleanExtra(KEY_IS_CAMERA, false) && getSupportFragmentManager().getBackStackEntryCount() == 0) {
        viewModel.onImageCaptureUndo(this);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onFolderSelected(@NonNull MediaFolder folder) {
    viewModel.onFolderSelected(folder.getBucketId());

    MediaPickerItemFragment fragment = MediaPickerItemFragment.newInstance(folder.getBucketId(), folder.getTitle(), viewModel.getMaxSelection());
    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.slide_from_right, R.anim.slide_to_left, R.anim.slide_from_left, R.anim.slide_to_right)
                               .replace(R.id.mediasend_fragment_container, fragment, TAG_ITEM_PICKER)
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onMediaSelected(@NonNull Media media) {
    viewModel.onSingleMediaSelected(this, media);
    navigateToMediaSend(recipient);
  }

  @Override
  public void onAddMediaClicked(@NonNull String bucketId) {
    // TODO: Get actual folder title somehow
    MediaPickerFolderFragment folderFragment = MediaPickerFolderFragment.newInstance(recipient);
    MediaPickerItemFragment   itemFragment   = MediaPickerItemFragment.newInstance(bucketId, "", viewModel.getMaxSelection());

    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.stationary, R.anim.slide_to_left, R.anim.slide_from_left, R.anim.slide_to_right)
                               .replace(R.id.mediasend_fragment_container, folderFragment, TAG_FOLDER_PICKER)
                               .addToBackStack(null)
                               .commit();

    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.slide_from_right, R.anim.stationary, R.anim.slide_from_left, R.anim.slide_to_right)
                               .replace(R.id.mediasend_fragment_container, itemFragment, TAG_ITEM_PICKER)
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onSendClicked(@NonNull List<Media> media, @NonNull String message) {
    viewModel.onSendClicked();

    ArrayList<Media> mediaList = new ArrayList<>(media);
    Intent           intent    = new Intent();

    intent.putParcelableArrayListExtra(EXTRA_MEDIA, mediaList);
    intent.putExtra(EXTRA_MESSAGE, message);
    setResult(RESULT_OK, intent);
    finish();

    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom);
  }

  @Override
  public void onNoMediaAvailable() {
    setResult(RESULT_CANCELED);
    finish();
  }

  @Override
  public void onTouchEventsNeeded(boolean needed) {
    MediaSendFragment fragment = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
    if (fragment != null) {
      fragment.onTouchEventsNeeded(needed);
    }
  }

  @Override
  public void onCameraError() {
    Toast.makeText(this, R.string.cameraErrorUnavailable, Toast.LENGTH_SHORT).show();
    setResult(RESULT_CANCELED, new Intent());
    finish();
  }

  @Override
  public void onImageCaptured(@NonNull byte[] data, int width, int height) {
    Log.i(TAG, "Camera image captured.");

    SimpleTask.run(getLifecycle(), () -> {
      try {
        Uri uri = BlobProvider.getInstance()
                              .forData(data)
                              .withMimeType(MediaTypes.IMAGE_JPEG)
                              .createForSingleSessionOnDisk(this, e -> Log.w(TAG, "Failed to write to disk.", e));
        return new Media(uri,
                         MediaTypes.IMAGE_JPEG,
                         System.currentTimeMillis(),
                         width,
                         height,
                         data.length,
                         Optional.of(Media.ALL_MEDIA_BUCKET_ID),
                         Optional.absent());
      } catch (IOException e) {
        return null;
      }
    }, media -> {
      if (media == null) {
        onNoMediaAvailable();
        return;
      }

      Log.i(TAG, "Camera capture stored: " + media.getUri().toString());

      viewModel.onImageCaptured(media);
      navigateToMediaSend(recipient);
    });
  }

  @Override
  public int getDisplayRotation() {
    return getWindowManager().getDefaultDisplay().getRotation();
  }

  private void initializeCountButtonObserver() {
    viewModel.getCountButtonState().observe(this, buttonState -> {
      if (buttonState == null) return;

      countButtonText.setText(String.valueOf(buttonState.getCount()));
      countButton.setEnabled(buttonState.isVisible());
      animateButtonVisibility(countButton, countButton.getVisibility(), buttonState.isVisible() ? View.VISIBLE : View.GONE);

      if (buttonState.getCount() > 0) {
        countButton.setOnClickListener(v -> navigateToMediaSend(recipient));
        if (buttonState.isVisible()) {
          animateButtonTextChange(countButton);
        }
      } else {
        countButton.setOnClickListener(null);
      }
    });
  }

  private void initializeCameraButtonObserver() {
    viewModel.getCameraButtonVisibility().observe(this, visible -> {
      if (visible == null) return;
      animateButtonVisibility(cameraButton, cameraButton.getVisibility(), visible ? View.VISIBLE : View.GONE);
    });
  }

  private void initializeErrorObserver() {
    viewModel.getError().observe(this, error -> {
      if (error == null) return;

      switch (error) {
        case ITEM_TOO_LARGE:
          Toast.makeText(this, R.string.attachmentsErrorSize, Toast.LENGTH_LONG).show();
          break;
        case TOO_MANY_ITEMS:
          // In modern session we'll say you can't sent more than 32 items, but if we ever want
          // the exact count of how many items the user attempted to send it's: viewModel.getMaxSelection()
          Toast.makeText(this, getString(R.string.attachmentsErrorNumber), Toast.LENGTH_SHORT).show();
          break;
      }
    });
  }

  private void navigateToMediaSend(@NonNull Recipient recipient) {
    MediaSendFragment fragment     = MediaSendFragment.newInstance(recipient);
    String            backstackTag = null;

    if (getSupportFragmentManager().findFragmentByTag(TAG_SEND) != null) {
      getSupportFragmentManager().popBackStack(TAG_SEND, FragmentManager.POP_BACK_STACK_INCLUSIVE);
      backstackTag = TAG_SEND;
    }

    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.slide_from_right, R.anim.slide_to_left, R.anim.slide_from_left, R.anim.slide_to_right)
                               .replace(R.id.mediasend_fragment_container, fragment, TAG_SEND)
                               .addToBackStack(backstackTag)
                               .commit();
  }

  private void navigateToCamera() {

    Context c = getApplicationContext();
    String permanentDenialTxt = Phrase.from(c, R.string.cameraGrantAccessDenied)
            .put(APP_NAME_KEY, c.getString(R.string.app_name))
            .format().toString();
    String requireCameraPermissionsTxt = Phrase.from(c, R.string.cameraGrantAccessDescription)
            .put(APP_NAME_KEY, c.getString(R.string.app_name))
            .format().toString();

    Permissions.with(this)
               .request(Manifest.permission.CAMERA)
               .withRationaleDialog(requireCameraPermissionsTxt, R.drawable.ic_baseline_photo_camera_48)
               .withPermanentDenialDialog(permanentDenialTxt)
               .onAllGranted(() -> {
                 Camera1Fragment fragment = getOrCreateCameraFragment();
                 getSupportFragmentManager().beginTransaction()
                                            .setCustomAnimations(R.anim.slide_from_right, R.anim.slide_to_left, R.anim.slide_from_left, R.anim.slide_to_right)
                                            .replace(R.id.mediasend_fragment_container, fragment, TAG_CAMERA)
                                            .addToBackStack(null)
                                            .commit();
               })
               .onAnyDenied(() -> Toast.makeText(MediaSendActivity.this, requireCameraPermissionsTxt, Toast.LENGTH_LONG).show())
               .execute();
  }

  private Camera1Fragment getOrCreateCameraFragment() {
    Camera1Fragment fragment = (Camera1Fragment) getSupportFragmentManager().findFragmentByTag(TAG_CAMERA);

    return fragment != null ? fragment
                            : Camera1Fragment.newInstance();
  }

  private void animateButtonVisibility(@NonNull View button, int oldVisibility, int newVisibility) {
    if (oldVisibility == newVisibility) return;

    if (button.getAnimation() != null) {
      button.clearAnimation();
      button.setVisibility(newVisibility);
    } else if (newVisibility == View.VISIBLE) {
      button.setVisibility(View.VISIBLE);

      Animation animation = new ScaleAnimation(0, 1, 0, 1, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      animation.setDuration(250);
      animation.setInterpolator(new OvershootInterpolator());
      button.startAnimation(animation);
    } else {
      Animation animation = new ScaleAnimation(1, 0, 1, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      animation.setDuration(150);
      animation.setInterpolator(new AccelerateDecelerateInterpolator());
      animation.setAnimationListener(new SimpleAnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
          button.clearAnimation();
          button.setVisibility(View.GONE);
        }
      });

      button.startAnimation(animation);
    }
  }

  private void animateButtonTextChange(@NonNull View button) {
    if (button.getAnimation() != null) {
      button.clearAnimation();
    }

    Animation grow = new ScaleAnimation(1f, 1.3f, 1f, 1.3f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    grow.setDuration(125);
    grow.setInterpolator(new AccelerateInterpolator());
    grow.setAnimationListener(new SimpleAnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        Animation shrink = new ScaleAnimation(1.3f, 1f, 1.3f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        shrink.setDuration(125);
        shrink.setInterpolator(new DecelerateInterpolator());
        button.startAnimation(shrink);
      }
    });

    button.startAnimation(grow);
  }

  @Override
  public void onRequestFullScreen(boolean fullScreen) {
    MediaSendFragment sendFragment = (MediaSendFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEND);
    if (sendFragment != null && sendFragment.isVisible()) {
      sendFragment.onRequestFullScreen(fullScreen);
    }
  }
}
