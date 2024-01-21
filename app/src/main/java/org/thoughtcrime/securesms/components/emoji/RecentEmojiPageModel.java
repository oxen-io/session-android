package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.session.libsignal.utilities.JsonUtil;
import org.session.libsignal.utilities.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import network.loki.messenger.R;

public class RecentEmojiPageModel implements EmojiPageModel {
  private static final String TAG                  = RecentEmojiPageModel.class.getSimpleName();
  private static final String EMOJI_LRU_PREFERENCE = "pref_recent_emoji2";
  private static final int    EMOJI_LRU_SIZE       = 50; // ACL - I DON'T SEE THE POINT OF THIS BEING 50 - WE ONLY DISPLAY 6!
  public static final  String KEY                  = "Recents";
  public static final List<String> DEFAULT_REACTIONS_LIST =
          Arrays.asList("\ud83d\ude02", "\ud83e\udd70", "\ud83d\ude22", "\ud83d\ude21", "\ud83d\ude2e", "\ud83d\ude08");

  private final SharedPreferences     prefs;
  private final LinkedHashSet<String> recentlyUsed;

  public RecentEmojiPageModel(Context context) {
    this.prefs        = PreferenceManager.getDefaultSharedPreferences(context);
    this.recentlyUsed = getPersistedCache();
  }

  private LinkedHashSet<String> getPersistedCache() {

    Log.d("[ACL]", "Hit getPersistedCache");

    String serialized = prefs.getString(EMOJI_LRU_PREFERENCE, "[]");
    try {
      CollectionType collectionType = TypeFactory.defaultInstance().constructCollectionType(LinkedHashSet.class, String.class);

      LinkedHashSet<String> emojiSet = JsonUtil.getMapper().readValue(serialized, collectionType);

      int x = 0;
      for (Iterator<String> iterator = emojiSet.iterator(); iterator.hasNext();) {
        Log.d("[ACL]", "Emoji " + x + " is: " + iterator.next());
      }

      return emojiSet;
      //return JsonUtil.getMapper().readValue(serialized, collectionType);
    } catch (IOException e) {
      Log.w(TAG, e);
      return new LinkedHashSet<>();
    }
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override public int getIconAttr() {
    return R.attr.emoji_category_recent;
  }

  @Override public List<String> getEmoji() {

    Log.d("[ACL]", "Hit getEmoji!");

    List<String> recent = new ArrayList<>(recentlyUsed);
    List<String> out = new ArrayList<>(DEFAULT_REACTIONS_LIST.size());

    for (int i = 0; i < DEFAULT_REACTIONS_LIST.size(); i++) {
      if (recent.size() > i) {
        out.add(recent.get(i));
      } else {
        out.add(DEFAULT_REACTIONS_LIST.get(i));
      }
    }

    return out;
  }

  @Override public List<Emoji> getDisplayEmoji() {
    return Stream.of(getEmoji()).map(Emoji::new).toList();
  }

  @Override public boolean hasSpriteMap() {
    return false;
  }

  @Nullable
  @Override
  public Uri getSpriteUri() {
    return null;
  }

  @Override public boolean isDynamic() {
    return true;
  }

  public void onCodePointSelected(String emoji) {

    Log.d("[ACL]", "Hit onCodePointSelected - we got: " + emoji + " - about to remove then add back to list (whyu?");

    // ACL - I guess this puts the emoji at the front of the most recently used list (DOES IT?!) - but what it
    // should really be doing is shuffling all recently used emojis to the right and the last one
    // falls off
    recentlyUsed.remove(emoji); // REMOVE IF EXISTS ANYWHERE IN LIST

    // ACL - INSTEAD OF JUST ADDING TO FRONT WE SHOULD FIRST SHIFT EVERYTHING ACROSS ONE

    recentlyUsed.add(emoji);    // PUT BACK AT FRONT OF LIST

    // ACL - I don't understand why EMOJI_LRU_SIZE is 50 but we only ever show 6 recently used emojis
    if (recentlyUsed.size() > EMOJI_LRU_SIZE) {

      Log.d("[ACL]", "recentlyUsed.size is greater than EMOJI_LRU_SIZE, i.e., " + recentlyUsed.size() + " > " + EMOJI_LRU_SIZE);

      Iterator<String> iterator = recentlyUsed.iterator();
      String aboutToRemoveThis = iterator.next();
      Log.d("[ACL]", "About to remove this: " + aboutToRemoveThis);
      iterator.remove();
    }

    final LinkedHashSet<String> latestRecentlyUsed = new LinkedHashSet<>(recentlyUsed);
    new AsyncTask<Void, Void, Void>() {

      @Override
      protected Void doInBackground(Void... params) {
        try {
          String serialized = JsonUtil.toJsonThrows(latestRecentlyUsed);
          prefs.edit()
               .putString(EMOJI_LRU_PREFERENCE, serialized)
               .apply();
        } catch (IOException e) {
          Log.w(TAG, e);
        }

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private String[] toReversePrimitiveArray(@NonNull LinkedHashSet<String> emojiSet) {
    String[] emojis = new String[emojiSet.size()];
    int i = emojiSet.size() - 1;
    for (String emoji : emojiSet) {
      emojis[i--] = emoji;
    }
    return emojis;
  }
}
