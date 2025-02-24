/* Copyright (C) 2013 Open Whisper Systems
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
package org.thoughtcrime.securesms;

import static nl.komponents.kovenant.android.KovenantAndroid.startKovenant;
import static nl.komponents.kovenant.android.KovenantAndroid.stopKovenant;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.work.Configuration;

import com.squareup.phrase.Phrase;

import org.conscrypt.Conscrypt;
import org.session.libsession.database.MessageDataProvider;
import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.session.libsession.messaging.groups.GroupManagerV2;
import org.session.libsession.messaging.groups.LegacyGroupDeprecationManager;
import org.session.libsession.messaging.notifications.TokenFetcher;
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier;
import org.session.libsession.messaging.sending_receiving.pollers.LegacyClosedGroupPollerV2;
import org.session.libsession.messaging.sending_receiving.pollers.Poller;
import org.session.libsession.snode.SnodeClock;
import org.session.libsession.snode.SnodeModule;
import org.session.libsession.utilities.Device;
import org.session.libsession.utilities.Environment;
import org.session.libsession.utilities.NonTranslatableStringConstants;
import org.session.libsession.utilities.ProfilePictureUtilities;
import org.session.libsession.utilities.SSKEnvironment;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Toaster;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.WindowDebouncer;
import org.session.libsignal.utilities.HTTP;
import org.session.libsignal.utilities.JsonUtil;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.ThreadUtils;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.configs.ConfigUploader;
import org.thoughtcrime.securesms.database.EmojiSearchDatabase;
import org.thoughtcrime.securesms.database.LastSentTimestampCache;
import org.thoughtcrime.securesms.database.LokiAPIDatabase;
import org.thoughtcrime.securesms.database.Storage;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.EmojiSearchData;
import org.thoughtcrime.securesms.debugmenu.DebugActivity;
import org.thoughtcrime.securesms.dependencies.AppComponent;
import org.thoughtcrime.securesms.dependencies.ConfigFactory;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.dependencies.DatabaseModule;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.groups.ExpiredGroupManager;
import org.thoughtcrime.securesms.groups.OpenGroupManager;
import org.thoughtcrime.securesms.groups.handler.AdminStateSync;
import org.thoughtcrime.securesms.groups.handler.CleanupInvitationHandler;
import org.thoughtcrime.securesms.groups.handler.DestroyedGroupSync;
import org.thoughtcrime.securesms.groups.GroupPollerManager;
import org.thoughtcrime.securesms.groups.handler.RemoveGroupMemberHandler;
import org.thoughtcrime.securesms.home.HomeActivity;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.AndroidLogger;
import org.thoughtcrime.securesms.logging.PersistentLogger;
import org.thoughtcrime.securesms.logging.UncaughtExceptionLogger;
import org.thoughtcrime.securesms.notifications.BackgroundPollManager;
import org.thoughtcrime.securesms.notifications.BackgroundPollWorker;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.PushRegistrationHandler;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sskenvironment.ReadReceiptManager;
import org.thoughtcrime.securesms.sskenvironment.TypingStatusRepository;
import org.thoughtcrime.securesms.util.AppVisibilityManager;
import org.thoughtcrime.securesms.util.Broadcaster;
import org.thoughtcrime.securesms.util.VersionDataFetcher;
import org.thoughtcrime.securesms.webrtc.CallMessageProcessor;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Provider;

import dagger.Lazy;
import dagger.hilt.EntryPoints;
import dagger.hilt.android.HiltAndroidApp;
import kotlin.Deprecated;
import kotlin.Unit;
import network.loki.messenger.BuildConfig;
import network.loki.messenger.R;

/**
 * Will be called once when the TextSecure process is created.
 * <p>
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
@HiltAndroidApp
public class ApplicationContext extends Application implements DefaultLifecycleObserver, Toaster, Configuration.Provider {

    public static final String PREFERENCES_NAME = "SecureSMS-Preferences";

    private static final String TAG = ApplicationContext.class.getSimpleName();

    public Poller poller = null;
    public Broadcaster broadcaster = null;
    private WindowDebouncer conversationListDebouncer;
    private HandlerThread conversationListHandlerThread;
    private Handler conversationListHandler;
    private PersistentLogger persistentLogger;

    @Inject HiltWorkerFactory workerFactory;
    @Inject LokiAPIDatabase lokiAPIDatabase;
    @Inject public Storage storage;
    @Inject Device device;
    @Inject MessageDataProvider messageDataProvider;
    @Inject TextSecurePreferences textSecurePreferences;
    @Inject ConfigFactory configFactory;
    @Inject LastSentTimestampCache lastSentTimestampCache;
    @Inject VersionDataFetcher versionDataFetcher;
    @Inject PushRegistrationHandler pushRegistrationHandler;
    @Inject TokenFetcher tokenFetcher;
    @Inject GroupManagerV2 groupManagerV2;
    @Inject SSKEnvironment.ProfileManagerProtocol profileManager;
    CallMessageProcessor callMessageProcessor;
    MessagingModuleConfiguration messagingModuleConfiguration;
    @Inject ConfigUploader configUploader;
    @Inject AdminStateSync adminStateSync;
    @Inject DestroyedGroupSync destroyedGroupSync;
    @Inject RemoveGroupMemberHandler removeGroupMemberHandler;
    @Inject SnodeClock snodeClock;
    @Inject ExpiringMessageManager expiringMessageManager;
    @Inject TypingStatusRepository typingStatusRepository;
    @Inject TypingStatusSender typingStatusSender;
    @Inject ReadReceiptManager readReceiptManager;
    @Inject Lazy<MessageNotifier> messageNotifierLazy;
    @Inject LokiAPIDatabase apiDB;
    @Inject EmojiSearchDatabase emojiSearchDb;
    @Inject LegacyClosedGroupPollerV2 legacyClosedGroupPollerV2;
    @Inject LegacyGroupDeprecationManager legacyGroupDeprecationManager;
    @Inject CleanupInvitationHandler cleanupInvitationHandler;
    @Inject BackgroundPollManager backgroundPollManager;  // Exists here only to start upon app starts
    @Inject AppVisibilityManager appVisibilityManager;  // Exists here only to start upon app starts
    @Inject GroupPollerManager groupPollerManager;  // Exists here only to start upon app starts
    @Inject ExpiredGroupManager expiredGroupManager; // Exists here only to start upon app starts

    public volatile boolean isAppVisible;
    public String KEYGUARD_LOCK_TAG = NonTranslatableStringConstants.APP_NAME + ":KeyguardLock";
    public String WAKELOCK_TAG      = NonTranslatableStringConstants.APP_NAME + ":WakeLock";

    @Override
    public Object getSystemService(String name) {
        if (MessagingModuleConfiguration.MESSAGING_MODULE_SERVICE.equals(name)) {
            return messagingModuleConfiguration;
        }
        return super.getSystemService(name);
    }

    public static ApplicationContext getInstance(Context context) {
        return (ApplicationContext) context.getApplicationContext();
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public TextSecurePreferences getPrefs() {
        return EntryPoints.get(getApplicationContext(), AppComponent.class).getPrefs();
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public DatabaseComponent getDatabaseComponent() {
        return EntryPoints.get(getApplicationContext(), DatabaseComponent.class);
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public MessageNotifier getMessageNotifier() {
        return messageNotifierLazy.get();
    }

    public Handler getConversationListNotificationHandler() {
        if (this.conversationListHandlerThread == null) {
            conversationListHandlerThread = new HandlerThread("ConversationListHandler");
            conversationListHandlerThread.start();
        }
        if (this.conversationListHandler == null) {
            conversationListHandler = new Handler(conversationListHandlerThread.getLooper());
        }
        return conversationListHandler;
    }

    public WindowDebouncer getConversationListDebouncer() {
        if (conversationListDebouncer == null) {
            conversationListDebouncer = new WindowDebouncer(1000, new Timer());
        }
        return conversationListDebouncer;
    }

    public PersistentLogger getPersistentLogger() {
        return this.persistentLogger;
    }

    @Override
    public void toast(@StringRes int stringRes, int toastLength, @NonNull Map<String, String> parameters) {
        Phrase builder = Phrase.from(this, stringRes);
        for (Map.Entry<String,String> entry : parameters.entrySet()) {
            builder.put(entry.getKey(), entry.getValue());
        }
        Toast.makeText(this, builder.format(), toastLength).show();
    }

    @Override
    public void toast(@NonNull CharSequence message, int toastLength) {
        Toast.makeText(this, message, toastLength).show();
    }

    @Override
    public void onCreate() {
        TextSecurePreferences.setPushSuffix(BuildConfig.PUSH_KEY_SUFFIX);

        DatabaseModule.init(this);
        MessagingModuleConfiguration.configure(this);
        super.onCreate();

        messagingModuleConfiguration = new MessagingModuleConfiguration(
                this,
                storage,
                device,
                messageDataProvider,
                configFactory,
                lastSentTimestampCache,
                this,
                tokenFetcher,
                groupManagerV2,
                snodeClock,
                textSecurePreferences,
                legacyClosedGroupPollerV2,
                legacyGroupDeprecationManager
                );
        callMessageProcessor = new CallMessageProcessor(this, textSecurePreferences, ProcessLifecycleOwner.get().getLifecycle(), storage);
        Log.i(TAG, "onCreate()");
        startKovenant();
        initializeSecurityProvider();
        initializeLogging();
        initializeCrashHandling();
        NotificationChannels.create(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        AppContext.INSTANCE.configureKovenant();
        broadcaster = new Broadcaster(this);
        boolean useTestNet = textSecurePreferences.getEnvironment() == Environment.TEST_NET;
        SnodeModule.Companion.configure(apiDB, broadcaster, useTestNet);
        SSKEnvironment.Companion.configure(typingStatusRepository, readReceiptManager, profileManager, getMessageNotifier(), expiringMessageManager);
        initializeWebRtc();
        initializeBlobProvider();
        resubmitProfilePictureIfNeeded();
        loadEmojiSearchIndexIfNeeded();
        EmojiSource.refresh();

        NetworkConstraint networkConstraint = new NetworkConstraint.Factory(this).create();
        HTTP.INSTANCE.setConnectedToNetwork(networkConstraint::isMet);

        snodeClock.start();
        pushRegistrationHandler.run();
        configUploader.start();
        removeGroupMemberHandler.start();
        destroyedGroupSync.start();
        adminStateSync.start();
        cleanupInvitationHandler.start();

        // add our shortcut debug menu if we are not in a release build
        if (BuildConfig.BUILD_TYPE != "release") {
            // add the config settings shortcut
            Intent intent = new Intent(this, DebugActivity.class);
            intent.setAction(Intent.ACTION_VIEW);

            ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(this, "shortcut_debug_menu")
                    .setShortLabel("Debug Menu")
                    .setLongLabel("Debug Menu")
                    .setIcon(IconCompat.createWithResource(this, R.drawable.ic_settings))
                    .setIntent(intent)
                    .build();

            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut);
        }
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        isAppVisible = true;
        Log.i(TAG, "App is now visible.");
        KeyCachingService.onAppForegrounded(this);

        // If the user account hasn't been created or onboarding wasn't finished then don't start
        // the pollers
        if (textSecurePreferences.getLocalNumber() == null) {
            return;
        }

        ThreadUtils.queue(()->{
            if (poller != null) {
                poller.setCaughtUp(false);
            }

            startPollingIfNeeded();

            OpenGroupManager.INSTANCE.startPolling();
            return Unit.INSTANCE;
        });

        // fetch last version data
        versionDataFetcher.startTimedVersionCheck();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isAppVisible = false;
        Log.i(TAG, "App is no longer visible.");
        KeyCachingService.onAppBackgrounded(this);
        getMessageNotifier().setVisibleThread(-1);
        if (poller != null) {
            poller.stopIfNeeded();
        }
        legacyClosedGroupPollerV2.stopAll();
        versionDataFetcher.stopTimedVersionCheck();
    }

    @Override
    public void onTerminate() {
        stopKovenant(); // Loki
        OpenGroupManager.INSTANCE.stopPolling();
        versionDataFetcher.stopTimedVersionCheck();
        super.onTerminate();
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public ExpiringMessageManager getExpiringMessageManager() {
        return expiringMessageManager;
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public TypingStatusRepository getTypingStatusRepository() {
        return typingStatusRepository;
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public TypingStatusSender getTypingStatusSender() {
        return typingStatusSender;
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public TextSecurePreferences getTextSecurePreferences() {
        return textSecurePreferences;
    }

    @Deprecated(message = "Use proper DI to inject this component")
    public ReadReceiptManager getReadReceiptManager() {
        return readReceiptManager;
    }


    public boolean isAppVisible() {
        return isAppVisible;
    }

    // Loki

    private void initializeSecurityProvider() {
        try {
            Class.forName("org.signal.aesgcmprovider.AesGcmCipher");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Failed to find AesGcmCipher class");
            throw new ProviderInitializationException();
        }

        int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
        Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

        if (aesPosition < 0) {
            Log.e(TAG, "Failed to install AesGcmProvider()");
            throw new ProviderInitializationException();
        }

        int conscryptPosition = Security.insertProviderAt(Conscrypt.newProvider(), 2);
        Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

        if (conscryptPosition < 0) {
            Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
        }
    }

    private void initializeLogging() {
        if (persistentLogger == null) {
            persistentLogger = new PersistentLogger(this);
        }
        Log.initialize(new AndroidLogger(), persistentLogger);
    }

    private void initializeCrashHandling() {
        final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger(originalHandler));
    }

    private void initializeWebRtc() {
        try {
            PeerConnectionFactory.initialize(InitializationOptions.builder(this).createInitializationOptions());
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, e);
        }
    }

    private void initializeBlobProvider() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            BlobProvider.getInstance().onSessionStart(this);
        });
    }

    private static class ProviderInitializationException extends RuntimeException { }
    private void setUpPollingIfNeeded() {
        String userPublicKey = textSecurePreferences.getLocalNumber();
        if (userPublicKey == null) return;
        poller = new Poller(configFactory, storage, lokiAPIDatabase);
    }

    public void startPollingIfNeeded() {
        setUpPollingIfNeeded();
        if (poller != null) {
            poller.startIfNeeded();
        }
        legacyClosedGroupPollerV2.start();
    }

    public void retrieveUserProfile() {
        setUpPollingIfNeeded();
        if (poller != null) {
            poller.retrieveUserProfile();
        }
    }

    private void resubmitProfilePictureIfNeeded() {
        ProfilePictureUtilities.INSTANCE.resubmitProfilePictureIfNeeded(this);
    }

    private void loadEmojiSearchIndexIfNeeded() {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (emojiSearchDb.query("face", 1).isEmpty()) {
                try (InputStream inputStream = getAssets().open("emoji/emoji_search_index.json")) {
                    List<EmojiSearchData> searchIndex = Arrays.asList(JsonUtil.fromJson(inputStream, EmojiSearchData[].class));
                    emojiSearchDb.setSearchIndex(searchIndex);
                } catch (IOException e) {
                    Log.e("Loki", "Failed to load emoji search index");
                }
            }
        });
    }

    // Method to clear the local data - returns true on success otherwise false
    @SuppressLint("ApplySharedPref")
    public boolean clearAllData() {
        TextSecurePreferences.clearAll(this);
        getSharedPreferences(PREFERENCES_NAME, 0).edit().clear().commit();
        if (!deleteDatabase(SQLCipherOpenHelper.DATABASE_NAME)) {
            Log.d("Loki", "Failed to delete database.");
            return false;
        }
        configFactory.clearAll();
        return true;
    }

    /**
     * Clear all local profile data and message history then restart the app after a brief delay.
     * @return true on success, false otherwise.
     */
    @SuppressLint("ApplySharedPref")
    public boolean clearAllDataAndRestart() {
        clearAllData();
        Util.runOnMain(() -> new Handler().postDelayed(ApplicationContext.this::restartApplication, 200));
        return true;
    }

    public void restartApplication() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(Intent.makeRestartActivityTask(intent.getComponent()));
        Runtime.getRuntime().exit(0);
    }

    // endregion

    // Method to wake up the screen and dismiss the keyguard
    public void wakeUpDeviceAndDismissKeyguardIfRequired() {
        // Get the KeyguardManager and PowerManager
        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager powerManager       = (PowerManager)getSystemService(Context.POWER_SERVICE);

        // Check if the phone is locked & if the screen is awake
        boolean isPhoneLocked = keyguardManager.isKeyguardLocked();
        boolean isScreenAwake = powerManager.isInteractive();

        if (!isScreenAwake) {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    WAKELOCK_TAG);

            // Acquire the wake lock to wake up the device
            wakeLock.acquire(3000);
        }

        // Dismiss the keyguard.
        // Note: This will not bypass any app-level (Session) lock; only the device-level keyguard.
        // TODO: When moving to a minimum Android API of 27, replace these deprecated calls with new APIs.
        if (isPhoneLocked) {
            KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock(KEYGUARD_LOCK_TAG);
            keyguardLock.disableKeyguard();
        }
    }

}
