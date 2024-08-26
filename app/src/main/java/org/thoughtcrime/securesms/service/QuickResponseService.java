package org.thoughtcrime.securesms.service;

import static org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Toast;
import com.squareup.phrase.Phrase;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import network.loki.messenger.R;
import org.session.libsession.messaging.messages.visible.VisibleMessage;
import org.session.libsession.messaging.sending_receiving.MessageSender;
import org.session.libsession.snode.SnodeAPI;
import org.session.libsession.utilities.Address;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.util.Rfc5724Uri;

public class QuickResponseService extends IntentService {

  private static final String TAG = QuickResponseService.class.getSimpleName();

  public QuickResponseService() {
    super("QuickResponseService");
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (intent == null) {
      Log.w(TAG, "Got null intent from QuickResponseService");
      return;
    }

    String actionString = intent.getAction();
    if (actionString == null) {
      Log.w(TAG, "Got null action from QuickResponseService intent");
      return;
    }

    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(actionString)) {
      Log.w(TAG, "Received unknown intent: " + intent.getAction());
      return;
    }

    if (KeyCachingService.isLocked(this)) {
      Log.w(TAG, "Got quick response request when locked...");
      Context c = getApplicationContext();
      String txt = Phrase.from(c, R.string.lockAppQuickResponse)
                      .put(APP_NAME_KEY, c.getString(R.string.app_name))
                      .format().toString();
      Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
      return;
    }

    try {
      Rfc5724Uri uri        = new Rfc5724Uri(intent.getDataString());
      String     content    = intent.getStringExtra(Intent.EXTRA_TEXT);
      String     number     = uri.getPath();

      if (number.contains("%")){
        number = URLDecoder.decode(number);
      }

      if (!TextUtils.isEmpty(content)) {
        VisibleMessage message = new VisibleMessage();
        message.setText(content);
        message.setSentTimestamp(SnodeAPI.getNowWithOffset());
        MessageSender.send(message, Address.fromExternal(this, number));
      }
    } catch (URISyntaxException e) {
      Toast.makeText(this, R.string.errorUnknown, Toast.LENGTH_LONG).show();
      Log.w(TAG, e);
    }
  }
}
