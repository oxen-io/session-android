package org.thoughtcrime.securesms.reactions.any;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.session.libsession.messaging.sending_receiving.MessageSender;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.concurrent.SignalExecutors;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.emoji.EmojiCategory;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.reactions.ReactionDetails;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import dagger.hilt.android.internal.ThreadUtil;
import network.loki.messenger.R;

final class ReactWithAnyEmojiRepository {

  private static final String TAG = Log.tag(ReactWithAnyEmojiRepository.class);

  private final Context                     context;
  private final RecentEmojiPageModel        recentEmojiPageModel;
  private final List<ReactWithAnyEmojiPage> emojiPages;

  ReactWithAnyEmojiRepository(@NonNull Context context, @NonNull String storageKey) {
    this.context              = context;
    this.recentEmojiPageModel = new RecentEmojiPageModel(context);
    this.emojiPages           = new LinkedList<>();

    emojiPages.addAll(Stream.of(EmojiSource.getLatest().getDisplayPages())
                            .filterNot(p -> p.getIconAttr() == EmojiCategory.EMOTICONS.getIcon())
                            .map(page -> new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(EmojiCategory.getCategoryLabel(page.getIconAttr()), page))))
                            .toList());
  }

  List<ReactWithAnyEmojiPage> getEmojiPageModels(@NonNull List<ReactionDetails> thisMessagesReactions) {
    List<ReactWithAnyEmojiPage> pages       = new LinkedList<>();
    List<String>                thisMessage = Stream.of(thisMessagesReactions)
                                                    .map(ReactionDetails::getDisplayEmoji)
                                                    .distinct()
                                                    .toList();

    if (thisMessage.isEmpty()) {
      pages.add(new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used, recentEmojiPageModel))));
    } else {
      pages.add(new ReactWithAnyEmojiPage(Arrays.asList(new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__this_message, new ThisMessageEmojiPageModel(thisMessage)),
                                                        new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used, recentEmojiPageModel))));
    }

    pages.addAll(emojiPages);

    return pages;
  }

  void addEmojiToMessage(@NonNull String emoji, @NonNull MessageId messageId) {
    SignalExecutors.BOUNDED.execute(() -> {
      ReactionRecord  oldRecord = Stream.of(DatabaseComponent.get(context).reactionDatabase().getReactions(messageId))
                                        .filter(record -> record.getAuthor().equals(TextSecurePreferences.getLocalNumber(context)))
                                        .findFirst()
                                        .orElse(null);

      if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
        //TODO: remove locally
        MessageSender.sendReactionRemoval(messageId.getId(), oldRecord.getEmoji());
      } else {
        //TODO: add locally
//        MessageSender.sendNewReaction(messageId.getId(), emoji);
//        ThreadUtil.runOnMain(() -> recentEmojiPageModel.onCodePointSelected(emoji));
      }
    });
  }
}
