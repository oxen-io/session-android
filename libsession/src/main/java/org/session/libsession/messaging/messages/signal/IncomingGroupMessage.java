package org.session.libsession.messaging.messages.signal;

public class IncomingGroupMessage extends IncomingTextMessage {

  private final boolean updateMessage;

  public IncomingGroupMessage(IncomingTextMessage base, String body, boolean updateMessage) {
    super(base, body);
    this.updateMessage = updateMessage;
  }

    @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isUpdateMessage() { return updateMessage; }

}
