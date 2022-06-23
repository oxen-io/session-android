package org.thoughtcrime.securesms.reactions.any;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.reactions.ReactionsRepository;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public final class ReactWithAnyEmojiViewModel extends ViewModel {

  private final ReactWithAnyEmojiRepository repository;
  private final long                        messageId;
  private final boolean                     isMms;

  private final Observable<MappingModelList>       emojiList;

  private ReactWithAnyEmojiViewModel(@NonNull ReactWithAnyEmojiRepository repository,
                                     long messageId,
                                     boolean isMms)
  {
    this.repository            = repository;
    this.messageId             = messageId;
    this.isMms                 = isMms;

    Observable<List<ReactWithAnyEmojiPage>> emojiPages = new ReactionsRepository().getReactions(new MessageId(messageId, isMms))
                                                                                  .map(thisMessagesReactions -> repository.getEmojiPageModels());

    this.emojiList = emojiPages.map(pages -> {
      MappingModelList list = new MappingModelList();

      for (ReactWithAnyEmojiPage page : pages) {
        String key = page.getKey();
        for (ReactWithAnyEmojiPageBlock block : page.getPageBlocks()) {
          list.add(new EmojiPageViewGridAdapter.EmojiHeader(key, block.getLabel()));
          list.addAll(toMappingModels(block.getPageModel()));
        }
      }

      return list;
    });

  }

  @NonNull Observable<MappingModelList> getEmojiList() {
    return emojiList.observeOn(AndroidSchedulers.mainThread());
  }

  void onEmojiSelected(@NonNull String emoji) {
    if (messageId > 0) {
      repository.addEmojiToMessage(emoji, new MessageId(messageId, isMms));
    }
  }

  private static @NonNull MappingModelList toMappingModels(@NonNull EmojiPageModel model) {
    return model.getDisplayEmoji()
                .stream()
                .map(e -> new EmojiPageViewGridAdapter.EmojiModel(model.getKey(), e))
                .collect(MappingModelList.collect());
  }

  static class Factory implements ViewModelProvider.Factory {

    private final ReactWithAnyEmojiRepository repository;
    private final long                        messageId;
    private final boolean                     isMms;

    Factory(@NonNull ReactWithAnyEmojiRepository repository, long messageId, boolean isMms) {
      this.repository = repository;
      this.messageId  = messageId;
      this.isMms      = isMms;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ReactWithAnyEmojiViewModel(repository, messageId, isMms));
    }
  }

}
