package org.thoughtcrime.securesms.reactions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.session.libsession.messaging.utilities.SessionId;
import org.thoughtcrime.securesms.database.model.MessageId;

import java.util.Collections;
import java.util.List;

import network.loki.messenger.R;

final class ReactionRecipientsAdapter extends RecyclerView.Adapter<ReactionRecipientsAdapter.ViewHolder> {

  private ReactionViewPagerAdapter.Listener callback;
  private List<ReactionDetails> data = Collections.emptyList();

  public ReactionRecipientsAdapter(ReactionViewPagerAdapter.Listener callback) {
    this.callback = callback;
  }

  public void updateData(List<ReactionDetails> newData) {
    data = newData;
    notifyDataSetChanged();
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(callback, LayoutInflater.from(parent.getContext())
                                        .inflate(R.layout.reactions_bottom_sheet_dialog_fragment_recipient_item,
                                                 parent,
                                                 false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(data.get(position));
  }

  @Override
  public int getItemCount() {
    return data.size();
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {

    private ReactionViewPagerAdapter.Listener callback;
    private final TextView        recipient;
    private final ImageView       remove;

    public ViewHolder(ReactionViewPagerAdapter.Listener callback, @NonNull View itemView) {
      super(itemView);
      this.callback = callback;
      recipient = itemView.findViewById(R.id.reactions_bottom_view_recipient_name);
      remove     = itemView.findViewById(R.id.reactions_bottom_view_recipient_emoji);
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
        String name = reaction.getSender().getName();
        if (name != null && new SessionId(name).getPrefix() != null) {
          name = name.substring(0, 4) + "..." + name.substring(name.length() - 4);
        }
        this.recipient.setText(name);
        this.remove.setVisibility(View.GONE);
      }
    }
  }

}
