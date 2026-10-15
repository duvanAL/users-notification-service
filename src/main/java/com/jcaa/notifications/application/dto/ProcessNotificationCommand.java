package com.jcaa.notifications.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable application input; independent of Kafka, Jackson and HTTP. */
public record ProcessNotificationCommand(
    UUID eventId, String eventName, Instant occurredOn, Map<String, String> payload) {
  public ProcessNotificationCommand {
    if (eventId == null || occurredOn == null || eventName == null || eventName.isBlank()
        || payload == null) {
      throw new IllegalArgumentException("Invalid notification command.");
    }
    payload = Map.copyOf(payload);
  }

  @Override
  public String toString() {
    return "ProcessNotificationCommand[eventId=" + eventId + ", eventName=" + eventName + "]";
  }
}
