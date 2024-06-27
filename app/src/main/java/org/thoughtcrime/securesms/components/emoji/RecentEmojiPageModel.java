package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.session.libsignal.utilities.JsonUtil;
import org.session.libsignal.utilities.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import network.loki.messenger.R;

public class RecentEmojiPageModel implements EmojiPageModel {
  private static final String TAG                  = RecentEmojiPageModel.class.getSimpleName();
  public static final String RECENT_EMOJIS_KEY    = "Recents";

  public static final LinkedList<String> DEFAULT_REACTION_EMOJIS_LIST = new LinkedList<>(Arrays.asList(
          "\ud83d\ude02",
          "\ud83e\udd70",
          "\ud83d\ude22",
          "\ud83d\ude21",
          "\ud83d\ude2e",
          "\ud83d\ude08"));

  public static final String DEFAULT_REACTION_EMOJIS_JSON_STRING = JsonUtil.toJson(new LinkedList<>(DEFAULT_REACTION_EMOJIS_LIST));
  private static SharedPreferences prefs;
  private static LinkedList<String> recentlyUsed;

  public RecentEmojiPageModel(Context context) {
    prefs = PreferenceManager.getDefaultSharedPreferences(context);

    // Note: Do NOT try to populate or update the persisted recent emojis in the constructor - the
    // `getEmoji` method ends up getting called half-way through in a race-condition manner.
  }

  @Override
  public String getKey() { return RECENT_EMOJIS_KEY; }

  @Override public int getIconAttr() { return R.attr.emoji_category_recent; }

  @Override public List<String> getEmoji() {
    // Populate our recently used list if required (i.e., on first run)
    if (recentlyUsed == null) {
      try {
        String recentlyUsedEmjoiJsonString = prefs.getString(RECENT_EMOJIS_KEY, DEFAULT_REACTION_EMOJIS_JSON_STRING);
        recentlyUsed = JsonUtil.fromJson(recentlyUsedEmjoiJsonString, LinkedList.class);
      } catch (Exception e) {
        Log.w(TAG, e);
        Log.d(TAG, "Default reaction emoji data was corrupt (likely via key re-use on app upgrade) - rewriting fresh data.");
        boolean writeSuccess = prefs.edit().putString(RECENT_EMOJIS_KEY, DEFAULT_REACTION_EMOJIS_JSON_STRING).commit();
        if (!writeSuccess) { Log.w(TAG, "Failed to update recently used emojis in shared prefs."); }
        recentlyUsed = DEFAULT_REACTION_EMOJIS_LIST;
      }
    }
    return new ArrayList<>(recentlyUsed);
  }

  @Override public List<Emoji> getDisplayEmoji() {
    return Stream.of(getEmoji()).map(Emoji::new).toList();
  }

  @Override public boolean hasSpriteMap() { return false; }

  @Nullable
  @Override
  public Uri getSpriteUri() { return null; }

  @Override public boolean isDynamic() { return true; }

  public static void onCodePointSelected(String emoji) {
    // If the emoji is already in the recently used list then remove it..
    if (recentlyUsed.contains(emoji)) { recentlyUsed.removeFirstOccurrence(emoji); }

    // ..and then regardless of whether the emoji used was already in the recently used list or not
    // it gets placed as the first element in the list..
    recentlyUsed.addFirst(emoji);

    // Ensure that we only ever store data for a maximum of 6 recently used emojis (this code will
    // execute if if we did NOT remove any occurrence of a previously used emoji but then added the
    // new emoji to the front of the list).
    while (recentlyUsed.size() > 6) { recentlyUsed.removeLast(); }

    // ..which we then save to shared prefs.
    String recentlyUsedAsJsonString = JsonUtil.toJson(recentlyUsed);
    boolean writeSuccess = prefs.edit().putString(RECENT_EMOJIS_KEY, recentlyUsedAsJsonString).commit();
    if (!writeSuccess) { Log.w(TAG, "Failed to update recently used emojis in shared prefs."); }
  }
}
