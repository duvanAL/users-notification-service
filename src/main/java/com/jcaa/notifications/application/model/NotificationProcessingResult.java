package com.jcaa.notifications.application.model;

import java.util.UUID;

public record NotificationProcessingResult(UUID eventId, String eventName, Status status) {
  public enum Status { SENT, IGNORED }
}
