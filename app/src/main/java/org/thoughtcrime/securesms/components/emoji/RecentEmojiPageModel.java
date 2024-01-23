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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import kotlin.jvm.internal.TypeReference;
import network.loki.messenger.R;

public class RecentEmojiPageModel implements EmojiPageModel {
  private static final String TAG                  = RecentEmojiPageModel.class.getSimpleName();
  //private static final String EMOJI_LRU_PREFERENCE = "pref_recent_emoji2";
  private static final int    EMOJI_LRU_SIZE       = 50; // ACL - I DON'T SEE THE POINT OF THIS BEING 50 - WE ONLY DISPLAY 6!
  public static final String RECENT_EMOJIS_KEY    = "Recents";

  public static final LinkedList<String> DEFAULT_REACTION_EMOJIS_LIST = new LinkedList<>(Arrays.asList(
          "\ud83d\ude02",
          "\ud83e\udd70",
          "\ud83d\ude22",
          "\ud83d\ude21",
          "\ud83d\ude2e",
          "\ud83d\ude08"));

  public static final String DEFAULT_REACTION_EMOJIS_JSON_STRING = JsonUtil.toJson(new LinkedList<>(DEFAULT_REACTION_EMOJIS_LIST));

  private final SharedPreferences prefs;
  private static LinkedList<String> recentlyUsed; //= getPersistedRecentEmojisFromPrefs();

  public RecentEmojiPageModel(Context context) {
    this.prefs        = PreferenceManager.getDefaultSharedPreferences(context);

    // We should not call methods from constructors in Java - `getEmoji` gets called half-way through our call to `getPersistedRecentEmojisFromPrefs`!!!
    //recentlyUsed = getPersistedRecentEmojisFromPrefs();
  }

  private void populateRecentEmojisFromPrefsIfRequired() {


    if (recentlyUsed == null) {
      Log.d("[ACL]", "recentlyUsed was null so attempting to populate from prefs.");
      //HashSet<String> recentlyUsedEmojiSet = prefs.getStringSet(R)
      //recentlyUsed = new LinkedList<>(prefs.getStringSet(RECENT_EMOJIS_KEY, DEFAULT_REACTION_EMOJIS_JSON_STRING));
    }
    Log.d("[ACL]", "recentlyUsed was not null - carrying on.");

    /*
    Log.d("[ACL]", "Hit getPersistedCache");

    //String serialized = prefs.getString(EMOJI_LRU_PREFERENCE, "[]");

    // If we couldn't find the saved recently used emojis list then use the default list
    //String serializedEmojisString = prefs.getString(RECENT_EMOJIS_KEY, Arrays.toString(DEFAULT_REACTIONS_LIST.toArray()));

     //RETURN THIS
    try {
      HashSet<String> emojiStringSet = (HashSet<String>) prefs.getStringSet(RECENT_EMOJIS_KEY, DEFAULT_REACTIONS_EMOJI_SET);
      Log.d("[ACL]", "Serialized linked hash set of emojis is: " + emojiStringSet.toString());
      Log.d("[ACL]", "DEFAULTS are: " + DEFAULT_REACTIONS_EMOJI_SET);
      //Log.d("[ACL]", "Converting this to actual emojis gives us: " + getDisplayEmoji());
      return new LinkedList<>(emojiStringSet);
    } catch (ClassCastException cce) {
      Log.w(TAG, "Prference data for RECENT_EMOIJIS_KEY was not a Set as expected - returning default recently used set.");
      return new LinkedList<>(DEFAULT_REACTIONS_EMOJI_SET);
    }
    */


    /*
    try {
      CollectionType collectionType = TypeFactory.defaultInstance().constructCollectionType(LinkedList.class, String.class);

      LinkedList<String> persistedRecentEmojis = JsonUtil.getMapper().readValue(serializedEmojisString, collectionType);

      Log.d("[ACL]", "Number of persistent recent emojis loaded: " + persistedRecentEmojis.size());

      int x = 0;
      for (Iterator<String> iterator = persistedRecentEmojis.iterator(); iterator.hasNext();) {
        Log.d("[ACL]", "Persisted recent emoji " + x + " is: " + iterator.next());
      }

      return persistedRecentEmojis;
      //return JsonUtil.getMapper().readValue(serialized, collectionType);
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
    */
  }

  @Override
  public String getKey() { return RECENT_EMOJIS_KEY; }

  @Override public int getIconAttr() { return R.attr.emoji_category_recent; }

  @Override public List<String> getEmoji() {

    /*
    prefs.edit().putStringSet(RECENT_EMOJIS_KEY, null).commit();

    return new ArrayList<String>();

     */
    //if (recentlyUsed == null) { Log.d("[ACL]", "recentlyUsed is NULL - WTF!"); } else { Log.d("[ACL]", "recentlyUsed is NOT null!!"); }

    //populateRecentEmojisFromPrefsIfRequired();

    if (recentlyUsed == null) {
      Log.d("[ACL]", "recentlyUsed was null so attempting to populate from prefs.");
      //HashSet<String> recentlyUsedEmojiSet = prefs.getStringSet(R)
      //String recentlyUsedEmojo
      try {
        String recentlyUsedEmjoiJsonString = prefs.getString(RECENT_EMOJIS_KEY, DEFAULT_REACTION_EMOJIS_JSON_STRING);

        //Type listType = LinkedList<String>(); //new TypeToken<List<String>>() {}.getType();

        recentlyUsed = JsonUtil.fromJson(recentlyUsedEmjoiJsonString, LinkedList.class); //new TypeToken(LinkedList.class);
        Log.d("[ACL]", "!!!!recentlyused is: " + recentlyUsed.toString());



        //recentlyUsed = JsonUtil.fromJson(recentlyUsedEmjoiJsonString, typeOf(LinkedList<String>)); //new LinkedList<>(Arrays.asList(JsonUtil.fromJson()));
      } catch (Exception e) {
        Log.w(TAG, e);
        Log.d(TAG, "Default reaction emoji data was corrupt (likely via key re-use on upgrade) - rewriting fresh data.");
        boolean writeSuccess = prefs.edit().putString(RECENT_EMOJIS_KEY, DEFAULT_REACTION_EMOJIS_JSON_STRING).commit();
        if (!writeSuccess) { Log.w(TAG, "Failed to update recently used emojis in shared prefs."); }
        recentlyUsed = DEFAULT_REACTION_EMOJIS_LIST;
      }
    }

    return new ArrayList<>(recentlyUsed);



    // RETURN THIS
    /*
    if (recentlyUsed != null) {// && recentlyUsed.size() == DEFAULT_REACTIONS_EMOJI_SET.size()) {
      Log.d(TAG, "Returning recently used emojis in getEmoji()");
      return new ArrayList<>(recentlyUsed); // Doing this to maintain `getEmoji()` returning a List rather than a LinkedHashSet (which is what `recentlyUsed` is)
    }
    */

    /*
    // Implied else that this is the first time we're asking for a recently used emoji
    Log.d(TAG, "Recently used was null so we'll grab and write to prefs.");
    recentlyUsed = getPersistedRecentEmojisFromPrefs();
    recentlyUsed = new LinkedList<>(DEFAULT_REACTIONS_EMOJI_SET);
    Boolean writeSuccess = prefs.edit().putStringSet(RECENT_EMOJIS_KEY, DEFAULT_REACTIONS_EMOJI_SET).commit();
    if (!writeSuccess) {
      Log.w(TAG, "Failed to write default reaction emojis to shared preferences!");
    } else {
      Log.d(TAG, "Wrote default reaction emojis to shared preferences successfully.");
    }

    return new ArrayList<>(recentlyUsed);
    */

  }
    /*
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
    */
  //}

  @Override public List<Emoji> getDisplayEmoji() {
    //return Stream.of(recentlyUsed).map(Emoji::new).toList();
    return Stream.of(getEmoji()).map(Emoji::new).toList();
  }

  @Override public boolean hasSpriteMap() { return false; }

  @Nullable
  @Override
  public Uri getSpriteUri() {
    Log.d("[ACL]", "getSpriteUri was called but it shouldn't be because we only ever return null!");
    return null;
  }

  @Override public boolean isDynamic() { return true; }

  public void onCodePointSelected(String emoji) {

    Log.d("[ACL]", "Hit onCodePointSelected - we got: " + emoji + " - about to remove then add back to list.");

    // If the emoji is already in the recently used list then move it to the front of the list
    if (recentlyUsed.contains(emoji)) {
      recentlyUsed.removeFirstOccurrence(emoji);
    }

    /*
    else // If the emoji wasn't already in the list put it at the front (far left / index 0) and push everything else right 1 position
    {
      recentlyUsed.removeLast();
    }
     */


    //recentlyUsed.add()

    // Regardless of whether the emoji used was already in the recently used list or not it gets
    // placed as the first element in the list and saved
    recentlyUsed.addFirst(emoji);

    // Write the updated recently used emojis to shared prefs
    String recentlyUsedAsJsonString = JsonUtil.toJson(recentlyUsed);
    boolean writeSuccess = prefs.edit().putString(RECENT_EMOJIS_KEY, recentlyUsedAsJsonString).commit();
    if (!writeSuccess) { Log.w(TAG, "Failed to update recently used emojis in shared prefs."); }

    //recentlyUsed.stream().findAny(emoji)

    // ACL - I guess this puts the emoji at the front of the most recently used list (DOES IT?!) - but what it
    // should really be doing is shuffling all recently used emojis to the right and the last one
    // falls off
    //recentlyUsed.remove(emoji); // REMOVE IF EXISTS ANYWHERE IN LIST

    // ACL - INSTEAD OF JUST ADDING TO FRONT WE SHOULD FIRST SHIFT EVERYTHING ACROSS ONE

    //recentlyUsed.add(emoji);    // PUT BACK AT FRONT OF LIST

    /*
    // ACL - I don't understand why EMOJI_LRU_SIZE is 50 but we only ever show 6 recently used emojis
    if (recentlyUsed.size() > EMOJI_LRU_SIZE) {

      Log.d("[ACL]", "recentlyUsed.size is greater than EMOJI_LRU_SIZE, i.e., " + recentlyUsed.size() + " > " + EMOJI_LRU_SIZE);

      Iterator<String> iterator = recentlyUsed.iterator();
      String aboutToRemoveThis = iterator.next();
      Log.d("[ACL]", "About to remove this: " + aboutToRemoveThis);
      iterator.remove();
    }
    */

    //String serializedRecentlyUsedEmojis = JsonUtil.toJson(recentlyUsed);
    //prefs.edit().putString(EMOJI_LRU_PREFERENCE, serializedRecentlyUsedEmojis).apply();
  }
    /*

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
       */

    /*
  private String[] toReversePrimitiveArray(@NonNull LinkedHashSet<String> emojiSet) {
    String[] emojis = new String[emojiSet.size()];
    int i = emojiSet.size() - 1;
    for (String emoji : emojiSet) {
      emojis[i--] = emoji;
    }
    return emojis;
  }*/
}
