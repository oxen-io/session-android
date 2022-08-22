package org.thoughtcrime.securesms.reactions;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.session.libsession.utilities.ThemeUtil;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.conversation.v2.ViewUtil;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.util.LifecycleDisposable;

import java.util.Objects;

import network.loki.messenger.R;

public final class ReactionsDialogFragment extends BottomSheetDialogFragment {

  private static final String ARGS_MESSAGE_ID = "reactions.args.message.id";
  private static final String ARGS_IS_MMS     = "reactions.args.is.mms";

  private ViewPager2                recipientPagerView;
  private ReactionViewPagerAdapter  recipientsAdapter;
  private Callback                  callback;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  public static DialogFragment create(MessageId messageId) {
    Bundle         args     = new Bundle();
    DialogFragment fragment = new ReactionsDialogFragment();

    args.putLong(ARGS_MESSAGE_ID, messageId.getId());
    args.putBoolean(ARGS_IS_MMS, messageId.isMms());

    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (getParentFragment() instanceof Callback) {
      callback = (Callback) getParentFragment();
    } else {
      callback = (Callback) context;
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    if (ThemeUtil.isDarkTheme(requireContext())) {
      setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_TextSecure_BottomSheetDialog_Fixed_ReactWithAny);
    } else {
      setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_TextSecure_Light_BottomSheetDialog_Fixed_ReactWithAny);
    }

    super.onCreate(savedInstanceState);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.reactions_bottom_sheet_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    recipientPagerView = view.findViewById(R.id.reactions_bottom_view_recipient_pager);

    disposables.bindTo(getViewLifecycleOwner());

    setUpRecipientsRecyclerView();
    setUpTabMediator(savedInstanceState);

    MessageId messageId = new MessageId(requireArguments().getLong(ARGS_MESSAGE_ID), requireArguments().getBoolean(ARGS_IS_MMS));
    setUpViewModel(messageId);
  }

  private void setUpTabMediator(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      FrameLayout    container       = requireDialog().findViewById(R.id.container);
      LayoutInflater layoutInflater  = LayoutInflater.from(requireContext());
      View           statusBarShader = layoutInflater.inflate(R.layout.react_with_any_emoji_status_fade, container, false);
      TabLayout      emojiTabs       = (TabLayout) layoutInflater.inflate(R.layout.reactions_bottom_sheet_dialog_fragment_tabs, container, false);

      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtil.getStatusBarHeight(container));

      statusBarShader.setLayoutParams(params);

      container.addView(statusBarShader, 0);
      container.addView(emojiTabs);

      ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> insets.consumeSystemWindowInsets());

      new TabLayoutMediator(emojiTabs, recipientPagerView, (tab, position) -> {
        tab.setCustomView(R.layout.reactions_bottom_sheet_dialog_fragment_emoji_item);

        View           customView = Objects.requireNonNull(tab.getCustomView());
        EmojiImageView emoji      = customView.findViewById(R.id.reactions_bottom_view_emoji_item_emoji);
        TextView       text       = customView.findViewById(R.id.reactions_bottom_view_emoji_item_text);
        EmojiCount     emojiCount = recipientsAdapter.getEmojiCount(position);

        emoji.setVisibility(View.VISIBLE);
        emoji.setImageEmoji(emojiCount.getDisplayEmoji());
        text.setText(String.valueOf(emojiCount.getCount()));
      }).attach();
    }
  }

  private void setUpRecipientsRecyclerView() {
    recipientsAdapter = new ReactionViewPagerAdapter(callback);

    recipientPagerView.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
      @Override
      public void onPageSelected(int position) {
        recipientPagerView.post(() -> recipientsAdapter.enableNestedScrollingForPosition(position));
      }

      @Override
      public void onPageScrollStateChanged(int state) {
        if (state == ViewPager2.SCROLL_STATE_IDLE) {
          recipientPagerView.requestLayout();
        }
      }
    });

    recipientPagerView.setAdapter(recipientsAdapter);
  }

  private void setUpViewModel(@NonNull MessageId messageId) {
    ReactionsViewModel.Factory factory = new ReactionsViewModel.Factory(messageId);

    ReactionsViewModel viewModel = new ViewModelProvider(this, factory).get(ReactionsViewModel.class);

    disposables.add(viewModel.getEmojiCounts().subscribe(emojiCounts -> {
      if (emojiCounts.size() <= 1) dismiss();

      recipientsAdapter.submitList(emojiCounts);
    }));
  }

  public interface Callback {
    void onRemoveReaction(@NonNull String emoji, @NonNull MessageId messageId, long timestamp);

    void onClearAll(@NonNull String emoji, @NonNull MessageId messageId);
  }

}
