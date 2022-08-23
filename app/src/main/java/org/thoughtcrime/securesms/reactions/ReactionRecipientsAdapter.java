package org.thoughtcrime.securesms.reactions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.database.model.MessageId;

import java.util.Collections;
import java.util.List;

import network.loki.messenger.R;

final class ReactionRecipientsAdapter extends RecyclerView.Adapter<ReactionRecipientsAdapter.ViewHolder> {

  private static final int HEADER_COUNT = 1;
  private static final int HEADER_POSITION = 0;

  private static final int HEADER_TYPE = 0;
  private static final int RECIPIENT_TYPE = 1;

  private ReactionViewPagerAdapter.Listener callback;
  private List<ReactionDetails> data = Collections.emptyList();
  private MessageId messageId;
  private boolean isUserModerator;
  private EmojiCount emojiData;

  public ReactionRecipientsAdapter(ReactionViewPagerAdapter.Listener callback) {
    this.callback = callback;
  }

  public void updateData(MessageId messageId, EmojiCount newData, boolean isUserModerator) {
    this.messageId = messageId;
    emojiData = newData;
    data = newData.getReactions();
    this.isUserModerator = isUserModerator;
    notifyDataSetChanged();
  }

  @Override
  public int getItemViewType(int position) {
    if (position == HEADER_POSITION) {
      return HEADER_TYPE;
    } else {
      return RECIPIENT_TYPE;
    }
  }

  @Override
  public @NonNull
  ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == HEADER_TYPE) {
      return new HeaderViewHolder(callback, LayoutInflater.from(parent.getContext()).inflate(R.layout.reactions_bottom_sheet_dialog_fragment_recycler_header, parent, false));
    } else {
      return new RecipientViewHolder(callback, LayoutInflater.from(parent.getContext()).inflate(R.layout.reactions_bottom_sheet_dialog_fragment_recipient_item, parent, false));
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    if (holder instanceof RecipientViewHolder) {
      ((RecipientViewHolder) holder).bind(data.get(position-HEADER_COUNT));
    } else if (holder instanceof HeaderViewHolder) {
      ((HeaderViewHolder) holder).bind(emojiData, messageId, isUserModerator);
    }
  }

  @Override
  public int getItemCount() {
    if (data.isEmpty()) {
      return 0;
    } else {
      return data.size() + HEADER_COUNT;
    }
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  static class HeaderViewHolder extends ViewHolder {

    private final ReactionViewPagerAdapter.Listener callback;

    public HeaderViewHolder(ReactionViewPagerAdapter.Listener callback, @NonNull View itemView) {
      super(itemView);
      this.callback = callback;
    }

    private void bind(@NonNull final EmojiCount emoji, final MessageId messageId, boolean isUserModerator) {
      View clearAll = itemView.findViewById(R.id.header_view_clear_all);
      clearAll.setVisibility(isUserModerator ? View.VISIBLE : View.GONE);
      clearAll.setOnClickListener(isUserModerator ? (View.OnClickListener) v -> {
        callback.onClearAll(emoji.getBaseEmoji(), messageId);
      } : null);
      TextView base = itemView.findViewById(R.id.header_view_emoji);
      base.setText(String.format("%s · %s", emoji.getDisplayEmoji(), emoji.getCount()));
    }
  }

  static final class RecipientViewHolder extends ViewHolder {

    private ReactionViewPagerAdapter.Listener callback;
    private final TextView recipient;
    private final ImageView remove;

    public RecipientViewHolder(ReactionViewPagerAdapter.Listener callback, @NonNull View itemView) {
      super(itemView);
      this.callback = callback;
      recipient = itemView.findViewById(R.id.reactions_bottom_view_recipient_name);
      remove = itemView.findViewById(R.id.reactions_bottom_view_recipient_emoji);
    }

    void bind(@NonNull ReactionDetails reaction) {
      this.remove.setOnClickListener((v) -> {
        MessageId messageId = new MessageId(reaction.getLocalId(), reaction.isMms());
        callback.onRemoveReaction(reaction.getBaseEmoji(), messageId, reaction.getTimestamp());
      });

      if (reaction.getSender().isLocalNumber()) {
        this.recipient.setText(R.string.ReactionsRecipientAdapter_you);
        this.remove.setVisibility(View.VISIBLE);
      } else {
        this.recipient.setText(reaction.getSender().getName());
        this.remove.setVisibility(View.GONE);
      }
    }
  }

}
