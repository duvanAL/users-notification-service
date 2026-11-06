package com.jcaa.notifications.application.port.out;

import java.util.UUID;

public interface NotificationHistoryPort {
  void record(UUID eventId, String userId, String eventName, String status, String failureCode);
}
