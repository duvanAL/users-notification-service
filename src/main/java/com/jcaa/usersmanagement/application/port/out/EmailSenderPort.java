package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;

public interface EmailSenderPort {
  void send(EmailDestinationModel destination);

  /** Sends with a stable provider idempotency key when the provider supports it. */
  default void send(final EmailDestinationModel destination, final String idempotencyKey) {
    send(destination);
  }
}
