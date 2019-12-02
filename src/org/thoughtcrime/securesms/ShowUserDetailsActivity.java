package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import network.loki.messenger.R;

@SuppressLint("StaticFieldLeak")
public class ShowUserDetailsActivity extends PassphraseRequiredActionBarActivity implements RecipientModifiedListener
{
  private static final String TAG = ShowUserDetailsActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA           = "recipient_address";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";


  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private Address                 address;



  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle instanceState, boolean ready) {
    setContentView(R.layout.show_user_details_activity);
    this.address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    Recipient recipient = Recipient.from(this, address, true);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setHeader(recipient);
    recipient.addListener(this);
    Bundle bundle = new Bundle();
    bundle.putParcelable(ADDRESS_EXTRA, address);
    initFragment(R.id.user_details_fragment, new UserDetailsFragment(), null, bundle);
  }



  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }


  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:{
        finish();
        return true;
      }
      default:
        break;
    }
    return super.onOptionsItemSelected(item);
  }


  private void setHeader(@NonNull Recipient recipient) {
    if      (recipient.isGroupRecipient())           setGroupRecipientTitle(recipient);
    else if (recipient.isLocalNumber())              setSelfTitle();
    else {
      String displayName = DatabaseFactory.getLokiUserDatabase(this).getDisplayName(address.serialize());
      if(displayName == null) {
        getSupportActionBar().setTitle(address.toString());
      }
      else {
        getSupportActionBar().setTitle(displayName);
      }
    }
  }

  private void setGroupRecipientTitle(Recipient recipient) {
    getSupportActionBar().setTitle(recipient.getName());
  }

  private void setSelfTitle() {
    getSupportActionBar().setTitle(getString(R.string.note_to_self));
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(() -> setHeader(recipient));
  }


  public static class UserDetailsFragment extends Fragment
          implements RecipientModifiedListener {

    private GlideRequests glideRequests;
    private Recipient recipient;
    private Button newMessageButton;
    private Button copyPubKeyButton;
    private TextView recipientId;
    private TextView label;
    private AvatarImageView avatar;


    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);
      glideRequests = GlideApp.with(this);
      initializeRecipients();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
      return inflater.inflate(R.layout.show_user_details_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
      super.onViewCreated(view, savedInstanceState);
      this.newMessageButton = ViewUtil.findById(view, R.id.button_new_message);
      this.copyPubKeyButton = ViewUtil.findById(view, R.id.button_copy_pub_key);
      this.recipientId = ViewUtil.findById(getView(), R.id.recipientId);
      this.label = ViewUtil.findById(getView(), R.id.labelPubKey);
      this.avatar = ViewUtil.findById(getView(), R.id.contact_photo_image);
    }

    @Override
    public void onResume() {
      super.onResume();
      setRecipientDetails(recipient);
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      this.recipient.removeListener(this);
    }

    private void initializeRecipients() {
      this.recipient = Recipient.from(getActivity(), getArguments().getParcelable(ADDRESS_EXTRA), true);
      this.recipient.addListener(this);
    }

    private void setRecipientDetails(Recipient recipient) {
      if(recipient.isGroupRecipient()) {
        this.label.setText(R.string.ShowUserDetailsActivity_groupId);
      }
      else {
        this.label.setText(R.string.fragment_new_conversation_public_key_edit_text_label);
      }
      this.avatar.setAvatar(glideRequests, recipient, false);
      this.avatar.setBackgroundColor(recipient.getColor().toActionBarColor(getActivity()));

      this.recipientId.setText(recipient.getAddress().toString());

      this.newMessageButton.setOnClickListener(view -> {
        Intent intent = new Intent(getActivity(), ConversationActivity.class);
        intent.putExtra(ConversationActivity.ADDRESS_EXTRA  , recipient.getAddress());
        intent.setDataAndType(getActivity().getIntent().getData(), getActivity().getIntent().getType());
        long existingThread = DatabaseFactory.getThreadDatabase(getActivity()).getThreadIdIfExistsFor(recipient);
        intent.putExtra(ShowUserDetailsActivity.THREAD_ID_EXTRA, existingThread);
        intent.putExtra(ShowUserDetailsActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
        startActivity(intent);
        getActivity().finish();
      });


      copyPubKeyButton.setOnClickListener(view -> {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setText(recipient.getAddress().toString());
      });
    }

    @Override
    public void onModified(final Recipient recipient) {
      Util.runOnMain(() -> {
        if (getContext() != null && getActivity() != null && !getActivity().isFinishing()) {
          setRecipientDetails(recipient);
        }
      });
    }
  }
}
