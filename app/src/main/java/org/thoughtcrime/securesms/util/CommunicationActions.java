package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.TaskStackBuilder;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;

public class CommunicationActions {

  public static void startConversation(@NonNull  Context          context,
                                       @NonNull  Recipient        recipient,
                                       @Nullable String           text,
                                       @Nullable TaskStackBuilder backStack)
  {
    new AsyncTask<Void, Void, Long>() {
      @Override
      protected Long doInBackground(Void... voids) {
        return DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(recipient);
      }

      @Override
      protected void onPostExecute(Long threadId) {
        Intent intent = new Intent(context, ConversationActivityV2.class);
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.getAddress());
        intent.putExtra(ConversationActivityV2.THREAD_ID, threadId);

        if (backStack != null) {
          backStack.addNextIntent(intent);
          backStack.startActivities();
        } else {
          context.startActivity(intent);
        }
      }
    }.execute();
  }
}
