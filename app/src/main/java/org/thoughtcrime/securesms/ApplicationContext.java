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
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import org.conscrypt.Conscrypt;
import org.session.libsession.avatars.AvatarHelper;
import org.session.libsession.database.MessageDataProvider;
import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier;
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPollerV2;
import org.session.libsession.messaging.sending_receiving.pollers.Poller;
import org.session.libsession.snode.SnodeModule;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.ConfigFactoryUpdateListener;
import org.session.libsession.utilities.Device;
import org.session.libsession.utilities.Environment;
import org.session.libsession.utilities.ProfilePictureUtilities;
import org.session.libsession.utilities.SSKEnvironment;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.WindowDebouncer;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageContextWrapper;
import org.session.libsession.utilities.dynamiclanguage.LocaleParser;
import org.session.libsignal.utilities.HTTP;
import org.session.libsignal.utilities.JsonUtil;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.ThreadUtils;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.KeyPairUtilities;
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
import org.thoughtcrime.securesms.groups.OpenGroupManager;
import org.thoughtcrime.securesms.home.HomeActivity;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.AndroidLogger;
import org.thoughtcrime.securesms.logging.PersistentLogger;
import org.thoughtcrime.securesms.logging.UncaughtExceptionLogger;
import org.thoughtcrime.securesms.notifications.BackgroundPollWorker;
import org.thoughtcrime.securesms.notifications.DefaultMessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.OptimizedMessageNotifier;
import org.thoughtcrime.securesms.notifications.PushRegistry;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sskenvironment.ProfileManager;
import org.thoughtcrime.securesms.sskenvironment.ReadReceiptManager;
import org.thoughtcrime.securesms.sskenvironment.TypingStatusRepository;
import org.thoughtcrime.securesms.util.Broadcaster;
import org.thoughtcrime.securesms.util.VersionDataFetcher;
import org.thoughtcrime.securesms.util.dynamiclanguage.LocaleParseHelper;
import org.thoughtcrime.securesms.webrtc.CallMessageProcessor;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.EntryPoints;
import dagger.hilt.android.HiltAndroidApp;
import kotlin.Unit;
import network.loki.messenger.BuildConfig;
import network.loki.messenger.R;
import network.loki.messenger.libsession_util.ConfigBase;
import network.loki.messenger.libsession_util.UserProfile;

/**
 * Will be called once when the TextSecure process is created.
 * <p>
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
@HiltAndroidApp
public class ApplicationContext extends Application implements DefaultLifecycleObserver, ConfigFactoryUpdateListener {

    public static final String PREFERENCES_NAME = "SecureSMS-Preferences";

    private static final String TAG = ApplicationContext.class.getSimpleName();

    private ExpiringMessageManager expiringMessageManager;
    private TypingStatusRepository typingStatusRepository;
    private TypingStatusSender typingStatusSender;
    private ReadReceiptManager readReceiptManager;
    private ProfileManager profileManager;
    public MessageNotifier messageNotifier = null;
    public Poller poller = null;
    public Broadcaster broadcaster = null;
    private WindowDebouncer conversationListDebouncer;
    private HandlerThread conversationListHandlerThread;
    private Handler conversationListHandler;
    private PersistentLogger persistentLogger;

    @Inject LokiAPIDatabase lokiAPIDatabase;
    @Inject public Storage storage;
    @Inject Device device;
    @Inject MessageDataProvider messageDataProvider;
    @Inject TextSecurePreferences textSecurePreferences;
    @Inject PushRegistry pushRegistry;
    @Inject ConfigFactory configFactory;
    @Inject LastSentTimestampCache lastSentTimestampCache;
    @Inject VersionDataFetcher versionDataFetcher;
    CallMessageProcessor callMessageProcessor;
    MessagingModuleConfiguration messagingModuleConfiguration;

    private volatile boolean isAppVisible;

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

    public TextSecurePreferences getPrefs() {
        return EntryPoints.get(getApplicationContext(), AppComponent.class).getPrefs();
    }

    public DatabaseComponent getDatabaseComponent() {
        return EntryPoints.get(getApplicationContext(), DatabaseComponent.class);
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
    public void notifyUpdates(@NonNull ConfigBase forConfigObject, long messageTimestamp) {
        // forward to the config factory / storage ig
        if (forConfigObject instanceof UserProfile && !textSecurePreferences.getConfigurationMessageSynced()) {
            textSecurePreferences.setConfigurationMessageSynced(true);
        }
        storage.notifyConfigUpdates(forConfigObject, messageTimestamp);
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
                ()-> KeyPairUtilities.INSTANCE.getUserED25519KeyPair(this),
                configFactory,
                lastSentTimestampCache
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
        messageNotifier = new OptimizedMessageNotifier(new DefaultMessageNotifier());
        broadcaster = new Broadcaster(this);
        LokiAPIDatabase apiDB = getDatabaseComponent().lokiAPIDatabase();
        boolean useTestNet = textSecurePreferences.getEnvironment() == Environment.TEST_NET;
        SnodeModule.Companion.configure(apiDB, broadcaster, useTestNet);
        initializeExpiringMessageManager();
        initializeTypingStatusRepository();
        initializeTypingStatusSender();
        initializeReadReceiptManager();
        initializeProfileManager();
        initializePeriodicTasks();
        SSKEnvironment.Companion.configure(getTypingStatusRepository(), getReadReceiptManager(), getProfileManager(), messageNotifier, getExpiringMessageManager());
        initializeWebRtc();
        initializeBlobProvider();
        resubmitProfilePictureIfNeeded();
        loadEmojiSearchIndexIfNeeded();
        EmojiSource.refresh();

        NetworkConstraint networkConstraint = new NetworkConstraint.Factory(this).create();
        HTTP.INSTANCE.setConnectedToNetwork(networkConstraint::isMet);

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
        });

        // fetch last version data
        versionDataFetcher.startTimedVersionCheck();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isAppVisible = false;
        Log.i(TAG, "App is no longer visible.");
        KeyCachingService.onAppBackgrounded(this);
        messageNotifier.setVisibleThread(-1);
        if (poller != null) {
            poller.stopIfNeeded();
        }
        ClosedGroupPollerV2.getShared().stopAll();
        versionDataFetcher.stopTimedVersionCheck();
    }

    @Override
    public void onTerminate() {
        stopKovenant(); // Loki
        OpenGroupManager.INSTANCE.stopPolling();
        versionDataFetcher.stopTimedVersionCheck();
        super.onTerminate();
    }

    public void initializeLocaleParser() {
        LocaleParser.Companion.configure(new LocaleParseHelper());
    }

    public ExpiringMessageManager getExpiringMessageManager() {
        return expiringMessageManager;
    }

    public TypingStatusRepository getTypingStatusRepository() {
        return typingStatusRepository;
    }

    public TypingStatusSender getTypingStatusSender() {
        return typingStatusSender;
    }

    public ReadReceiptManager getReadReceiptManager() {
        return readReceiptManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
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

    private void initializeExpiringMessageManager() {
        this.expiringMessageManager = new ExpiringMessageManager(this);
    }

    private void initializeTypingStatusRepository() {
        this.typingStatusRepository = new TypingStatusRepository();
    }

    private void initializeReadReceiptManager() {
        this.readReceiptManager = new ReadReceiptManager();
    }

    private void initializeProfileManager() {
        this.profileManager = new ProfileManager(this, configFactory);
    }

    private void initializeTypingStatusSender() {
        this.typingStatusSender = new TypingStatusSender(this);
    }

    private void initializePeriodicTasks() {
        BackgroundPollWorker.schedulePeriodic(this);
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

    @Override
    protected void attachBaseContext(Context base) {
        initializeLocaleParser();
        super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(base, TextSecurePreferences.getLanguage(base)));
    }

    private static class ProviderInitializationException extends RuntimeException { }
    private void setUpPollingIfNeeded() {
        String userPublicKey = TextSecurePreferences.getLocalNumber(this);
        if (userPublicKey == null) return;
        if (poller != null) {
            poller.setUserPublicKey(userPublicKey);
            return;
        }
        poller = new Poller(configFactory, new Timer());
    }

    public void startPollingIfNeeded() {
        setUpPollingIfNeeded();
        if (poller != null) {
            poller.startIfNeeded();
        }
        ClosedGroupPollerV2.getShared().start();
    }

    public void retrieveUserProfile() {
        setUpPollingIfNeeded();
        if (poller != null) {
            poller.retrieveUserProfile();
        }
    }

    private void resubmitProfilePictureIfNeeded() {
        // Files expire on the file server after a while, so we simply re-upload the user's profile picture
        // at a certain interval to ensure it's always available.
        String userPublicKey = TextSecurePreferences.getLocalNumber(this);
        if (userPublicKey == null) return;
        long now = new Date().getTime();
        long lastProfilePictureUpload = TextSecurePreferences.getLastProfilePictureUpload(this);
        if (now - lastProfilePictureUpload <= 14 * 24 * 60 * 60 * 1000) return;
        ThreadUtils.queue(() -> {
            // Don't generate a new profile key here; we do that when the user changes their profile picture
            Log.d("Loki-Avatar", "Uploading Avatar Started");
            String encodedProfileKey = TextSecurePreferences.getProfileKey(ApplicationContext.this);
            try {
                // Read the file into a byte array
                InputStream inputStream = AvatarHelper.getInputStreamFor(ApplicationContext.this, Address.fromSerialized(userPublicKey));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int count;
                byte[] buffer = new byte[1024];
                while ((count = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    baos.write(buffer, 0, count);
                }
                baos.flush();
                byte[] profilePicture = baos.toByteArray();
                // Re-upload it
                ProfilePictureUtilities.INSTANCE.upload(profilePicture, encodedProfileKey, ApplicationContext.this).success(unit -> {
                    // Update the last profile picture upload date
                    TextSecurePreferences.setLastProfilePictureUpload(ApplicationContext.this, new Date().getTime());
                    Log.d("Loki-Avatar", "Uploading Avatar Finished");
                    return Unit.INSTANCE;
                });
            } catch (Exception e) {
                Log.e("Loki-Avatar", "Uploading avatar failed.");
            }
        });
    }

    private void loadEmojiSearchIndexIfNeeded() {
        Executors.newSingleThreadExecutor().execute(() -> {
            EmojiSearchDatabase emojiSearchDb = getDatabaseComponent().emojiSearchDatabase();
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

    /**
     * Clear all local profile data and message history.
     * @return true on success, false otherwise.
     */
    @SuppressLint("ApplySharedPref")
    public boolean clearAllData() {
        TextSecurePreferences.clearAll(this);
        getSharedPreferences(PREFERENCES_NAME, 0).edit().clear().commit();
        if (!deleteDatabase(SQLCipherOpenHelper.DATABASE_NAME)) {
            Log.d("Loki", "Failed to delete database.");
            return false;
        }
        configFactory.keyPairChanged();
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
}
