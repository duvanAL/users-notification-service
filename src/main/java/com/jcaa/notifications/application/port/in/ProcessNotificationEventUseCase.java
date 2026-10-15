package com.jcaa.notifications.application.port.in;

import com.jcaa.notifications.application.dto.ProcessNotificationCommand;
import com.jcaa.notifications.application.model.NotificationProcessingResult;

public interface ProcessNotificationEventUseCase {
  NotificationProcessingResult process(ProcessNotificationCommand command);
}
