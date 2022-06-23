package org.thoughtcrime.securesms.reactions.any;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.session.libsession.messaging.sending_receiving.MessageSender;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.concurrent.SignalExecutors;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;
import org.thoughtcrime.securesms.database.ReactionDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.emoji.EmojiCategory;
import org.thoughtcrime.securesms.emoji.EmojiSource;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import network.loki.messenger.R;

final class ReactWithAnyEmojiRepository {

  private static final String TAG = Log.tag(ReactWithAnyEmojiRepository.class);

  private final Context                     context;
  private final RecentEmojiPageModel        recentEmojiPageModel;
  private final List<ReactWithAnyEmojiPage> emojiPages;

  ReactWithAnyEmojiRepository(@NonNull Context context) {
    this.context              = context;
    this.recentEmojiPageModel = new RecentEmojiPageModel(context);
    this.emojiPages           = new LinkedList<>();

    emojiPages.addAll(Stream.of(EmojiSource.getLatest().getDisplayPages())
                            .filterNot(p -> p.getIconAttr() == EmojiCategory.EMOTICONS.getIcon())
                            .map(page -> new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(EmojiCategory.getCategoryLabel(page.getIconAttr()), page))))
                            .toList());
  }

  List<ReactWithAnyEmojiPage> getEmojiPageModels() {
    List<ReactWithAnyEmojiPage> pages       = new LinkedList<>();

    pages.add(new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used, recentEmojiPageModel))));
    pages.addAll(emojiPages);

    return pages;
  }

  void addEmojiToMessage(@NonNull String emoji, @NonNull MessageId messageId) {
    SignalExecutors.BOUNDED.execute(() -> {
      ReactionDatabase reactionDb = DatabaseComponent.get(context).reactionDatabase();
      String author = TextSecurePreferences.getLocalNumber(context);
      ReactionRecord  oldRecord = Stream.of(reactionDb.getReactions(messageId))
                                        .filter(record -> record.getAuthor().equals(author))
                                        .findFirst()
                                        .orElse(null);

      if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
        //TODO: DatabaseComponent.get(context).reactionDatabase().deleteReaction(messageId);
        MessageSender.sendReactionRemoval(messageId.getId(), oldRecord.getEmoji());
      } else {
        long timestamp = System.currentTimeMillis();
        ReactionRecord reaction = new ReactionRecord(0, messageId.getId(), author, emoji, "", timestamp, timestamp);
        DatabaseComponent.get(context).reactionDatabase().addReaction(messageId, reaction);
        /*TODO: MessageSender.sendNewReaction(messageId.getId(), emoji);
        ThreadUtil.runOnMain(() -> recentEmojiPageModel.onCodePointSelected(emoji));*/
      }
    });
  }
}
