package org.thoughtcrime.securesms.conversation.v2.messages;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;

import com.annimon.stream.Stream;

import org.session.libsession.utilities.TextSecurePreferences;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.conversation.v2.ViewUtil;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.ReactionRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import network.loki.messenger.R;

public class EmojiReactionsView extends LinearLayout {

  // Normally 6dp, but we have 1dp left+right margin on the pills themselves
  private static final int OUTER_MARGIN = ViewUtil.dpToPx(5);

  private boolean              outgoing;
  private List<ReactionRecord> records;
  private int                  bubbleWidth;
  private ViewGroup            container;
  private Group                showLess;
  private VisibleMessageViewDelegate delegate;

  public EmojiReactionsView(Context context) {
    super(context);
    init(null);
  }

  public EmojiReactionsView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.view_emoji_reactions, this);

    this.container = findViewById(R.id.layout_emoji_container);
    this.showLess = findViewById(R.id.group_show_less);

    records = new ArrayList<>();

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiReactionsView, 0, 0);
      outgoing = typedArray.getBoolean(R.styleable.EmojiReactionsView_erv_outgoing, false);
    }
  }

  public void clear() {
    this.records.clear();
    this.bubbleWidth = 0;
    container.removeAllViews();
  }

  public void setReactions(@NonNull List<ReactionRecord> records, boolean outgoing, int bubbleWidth, VisibleMessageViewDelegate delegate) {
    if (records.equals(this.records) && this.bubbleWidth == bubbleWidth) {
      return;
    }

    this.records.clear();
    this.records.addAll(records);

    this.outgoing = outgoing;
    this.bubbleWidth = bubbleWidth;
    this.delegate = delegate;

    displayReactions(6);
  }

  private void displayReactions(int threshold) {
    String userPublicKey     = TextSecurePreferences.getLocalNumber(getContext());
    List<Reaction> reactions = buildSortedReactionsList(records, userPublicKey, threshold);

    container.removeAllViews();

    for (Reaction reaction : reactions) {
      View pill = buildPill(getContext(), this, reaction);
      pill.setVisibility(bubbleWidth == 0 ? INVISIBLE : VISIBLE);
      pill.setOnClickListener(v -> onReactionClicked(reaction));
      container.addView(pill);
    }

    if (threshold == Integer.MAX_VALUE) {
      showLess.setVisibility(VISIBLE);
      for (int id : showLess.getReferencedIds()) {
        findViewById(id).setOnClickListener(view -> displayReactions(6));
      }
    } else {
      showLess.setVisibility(GONE);
    }
    measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

    int railWidth = getMeasuredWidth();

    if (railWidth < (bubbleWidth - OUTER_MARGIN)) {
      int margin = (bubbleWidth - railWidth - OUTER_MARGIN);

      if (outgoing) {
        ViewUtil.setRightMargin(this, margin);
      } else {
        ViewUtil.setLeftMargin(this, margin);
      }
    } else {
      if (outgoing) {
        ViewUtil.setRightMargin(this, OUTER_MARGIN);
      } else {
        ViewUtil.setLeftMargin(this, OUTER_MARGIN);
      }
    }
  }

  private void onReactionClicked(Reaction reaction) {
    if (reaction.messageId != 0) {
      MessageId messageId = new MessageId(reaction.messageId, reaction.isMms);
      delegate.onReactionClicked(reaction.displayEmoji, messageId, reaction.userWasSender);
    } else {
      displayReactions(Integer.MAX_VALUE);
    }
  }

  private static @NonNull List<Reaction> buildSortedReactionsList(@NonNull List<ReactionRecord> records, String userPublicKey, int threshold) {
    Map<String, Reaction> counters = new LinkedHashMap<>();

    for (ReactionRecord record : records) {
      String   baseEmoji = EmojiUtil.getCanonicalRepresentation(record.getEmoji());
      Reaction info      = counters.get(baseEmoji);

      if (info == null) {
        info = new Reaction(record.getMessageId(), record.isMms(), baseEmoji, record.getEmoji(), 1, record.getDateReceived(), userPublicKey.equals(record.getAuthor()));
      } else {
        info.update(record.getEmoji(), record.getDateReceived(), userPublicKey.equals(record.getAuthor()));
      }

      counters.put(baseEmoji, info);
    }

    List<Reaction> reactions = new ArrayList<>(counters.values());

    Collections.sort(reactions, Collections.reverseOrder());

    if (reactions.size() > threshold) {
      List<Reaction> shortened = new ArrayList<>(threshold - 1);
      shortened.addAll(reactions.subList(0, threshold - 2));
      shortened.add(Stream.of(reactions).skip(threshold - 2).reduce(new Reaction(0, false, null, null, 0, 0, false), Reaction::merge));

      return shortened;
    } else {
      return reactions;
    }
  }

  private static View buildPill(@NonNull Context context, @NonNull ViewGroup parent, @NonNull Reaction reaction) {
    View           root      = LayoutInflater.from(context).inflate(R.layout.reactions_pill, parent, false);
    EmojiImageView emojiView = root.findViewById(R.id.reactions_pill_emoji);
    TextView       countView = root.findViewById(R.id.reactions_pill_count);
    View           spacer    = root.findViewById(R.id.reactions_pill_spacer);

    if (reaction.displayEmoji != null) {
      emojiView.setImageEmoji(reaction.displayEmoji);

      if (reaction.count > 1) {
        countView.setText(String.valueOf(reaction.count));
      } else {
        countView.setVisibility(GONE);
        spacer.setVisibility(GONE);
      }
    } else {
      emojiView.setVisibility(GONE);
      spacer.setVisibility(GONE);
      countView.setText(context.getString(R.string.ReactionsConversationView_plus, reaction.count));
    }

    if (reaction.userWasSender) {
      root.setBackground(ContextCompat.getDrawable(context, R.drawable.reaction_pill_background_selected));
      countView.setTextColor(ContextCompat.getColor(context, R.color.reactions_pill_selected_text_color));
    } else {
      root.setBackground(ContextCompat.getDrawable(context, R.drawable.reaction_pill_background));
    }

    return root;
  }

  private static class Reaction implements Comparable<Reaction> {
    private long messageId;
    private boolean isMms;
    private String  baseEmoji;
    private String  displayEmoji;
    private int     count;
    private long    lastSeen;
    private boolean userWasSender;

    Reaction(long messageId, boolean isMms, @Nullable String baseEmoji, @Nullable String displayEmoji, int count, long lastSeen, boolean userWasSender) {
      this.messageId     = messageId;
      this.isMms         = isMms;
      this.baseEmoji     = baseEmoji;
      this.displayEmoji  = displayEmoji;
      this.count         = count;
      this.lastSeen      = lastSeen;
      this.userWasSender = userWasSender;
    }

    void update(@NonNull String displayEmoji, long lastSeen, boolean userWasSender) {
      if (!this.userWasSender) {
        if (userWasSender || lastSeen > this.lastSeen) {
          this.displayEmoji = displayEmoji;
        }
      }

      this.count         = this.count + 1;
      this.lastSeen      = Math.max(this.lastSeen, lastSeen);
      this.userWasSender = this.userWasSender || userWasSender;
    }

    @NonNull Reaction merge(@NonNull Reaction other) {
      this.count         = this.count + other.count;
      this.lastSeen      = Math.max(this.lastSeen, other.lastSeen);
      this.userWasSender = this.userWasSender || other.userWasSender;
      return this;
    }

    @Override
    public int compareTo(Reaction rhs) {
      Reaction lhs = this;

      if (lhs.count != rhs.count) {
        return Integer.compare(lhs.count, rhs.count);
      }

      return Long.compare(lhs.lastSeen, rhs.lastSeen);
    }
  }
}