package com.jcaa.usersmanagement.domain.port.out;

import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;

public interface EmailSenderPort {
  void send(EmailDestinationModel destination);

  /** Keep the same identity when retrying a delivery accepted by the provider. */
  default void send(EmailDestinationModel destination, String idempotencyKey) {
    send(destination);
  }
}
