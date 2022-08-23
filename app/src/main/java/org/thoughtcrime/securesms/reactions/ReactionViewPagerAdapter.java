package org.thoughtcrime.securesms.reactions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.util.adapter.AlwaysChangedDiffUtil;

import java.util.List;

import network.loki.messenger.R;

/**
 * ReactionViewPagerAdapter provides pages to a ViewPager2 which contains the reactions on a given message.
 */
class ReactionViewPagerAdapter extends ListAdapter<EmojiCount, ReactionViewPagerAdapter.ViewHolder> {

  private static final int HEADER_COUNT = 1;
  private static final int HEADER_POSITION = 0;

  private static final int HEADER_TYPE = 0;
  private static final int RECIPIENT_TYPE = 1;

  private Listener callback;
  private int selectedPosition = 0;
  private MessageId messageId = null;
  private boolean isUserModerator = false;

  protected ReactionViewPagerAdapter(Listener callback) {
    super(new AlwaysChangedDiffUtil<>());
    this.callback = callback;
  }

  public void setIsUserModerator(boolean isUserModerator) {
    this.isUserModerator = isUserModerator;
  }

  public void setMessageId(MessageId messageId) {
    this.messageId = messageId;
  }

  @Override
  public int getItemViewType(int position) {
    if (position == 0) {
      return HEADER_TYPE;
    } else {
      return RECIPIENT_TYPE;
    }
  }

  @Override
  public int getItemCount() {
    return super.getItemCount() + HEADER_COUNT;
  }

  @NonNull EmojiCount getEmojiCount(int position) {
    return getItem(position);
  }

  void enableNestedScrollingForPosition(int position) {
    selectedPosition = position;

    notifyItemRangeChanged(0, getItemCount(), new Object());
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == HEADER_TYPE) {
      return new HeaderViewHolder(callback, LayoutInflater.from(parent.getContext()).inflate(R.layout.reactions_bottom_sheet_dialog_fragment_recycler_header, parent, false));
    } else {
      return new RecipientViewHolder(callback, LayoutInflater.from(parent.getContext()).inflate(R.layout.reactions_bottom_sheet_dialog_fragment_recycler, parent, false));
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
    } else if (position != HEADER_POSITION) {
      RecipientViewHolder viewHolder = (RecipientViewHolder) holder;
      viewHolder.setSelected(selectedPosition);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    if (position == HEADER_POSITION) {
      HeaderViewHolder viewHolder = (HeaderViewHolder) holder;
      viewHolder.bind(getItem(position), messageId, isUserModerator);
    } else {
      RecipientViewHolder viewHolder = (RecipientViewHolder) holder;
      viewHolder.onBind(getItem(position));
      viewHolder.setSelected(selectedPosition);
    }
  }

  @Override
  public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
    recyclerView.setNestedScrollingEnabled(false);
    ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
    params.height = (int) (recyclerView.getResources().getDisplayMetrics().heightPixels * 0.80);
    recyclerView.setLayoutParams(params);
    recyclerView.setHasFixedSize(true);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  static class HeaderViewHolder extends ViewHolder {

    private final Listener callback;

    public HeaderViewHolder(Listener callback, @NonNull View itemView) {
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
      base.setText(String.format("%s · %s", emoji.getBaseEmoji(), emoji.getDisplayEmoji()));
    }

  }

  static class RecipientViewHolder extends ViewHolder {

    private final RecyclerView              recycler;
    private final ReactionRecipientsAdapter adapter;

    public RecipientViewHolder(Listener callback, @NonNull View itemView) {
      super(itemView);
      adapter = new ReactionRecipientsAdapter(callback);
      recycler = itemView.findViewById(R.id.reactions_bottom_view_recipient_recycler);

      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                 ViewGroup.LayoutParams.MATCH_PARENT);

      recycler.setLayoutParams(params);
      recycler.setAdapter(adapter);
    }

    public void onBind(@NonNull EmojiCount emojiCount) {
      adapter.updateData(emojiCount.getReactions());
    }

    public void setSelected(int position) {
      recycler.setNestedScrollingEnabled(getAdapterPosition() == position);
    }
  }

  public interface Listener {
    void onRemoveReaction(@NonNull String emoji, @NonNull MessageId messageId, long timestamp);

    void onClearAll(@NonNull String emoji, @NonNull MessageId messageId);
  }

}
