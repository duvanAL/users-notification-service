package com.jcaa.notifications.application.service;

import com.jcaa.notifications.application.dto.ProcessNotificationCommand;
import com.jcaa.notifications.application.mapper.NotificationEmailMapper;
import com.jcaa.notifications.application.model.NotificationEventType;
import com.jcaa.notifications.application.model.NotificationProcessingResult;
import com.jcaa.notifications.application.port.in.ProcessNotificationEventUseCase;
import com.jcaa.usersmanagement.domain.port.out.EmailSenderPort;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventApplicationService implements ProcessNotificationEventUseCase {
  private final EmailSenderPort email;
  private final NotificationEmailMapper mapper;

  public NotificationEventApplicationService(EmailSenderPort email, NotificationEmailMapper mapper) {
    this.email = email;
    this.mapper = mapper;
  }

  @Override
  public NotificationProcessingResult process(ProcessNotificationCommand command) {
    if (command == null) throw new IllegalArgumentException("Notification command is required.");
    NotificationEventType type = NotificationEventType.from(command.eventName());
    if (type == NotificationEventType.USER_DELETED) {
      String userId = command.payload().get("userId");
      if (userId == null || userId.isBlank()) {
        throw new IllegalArgumentException("Deleted event requires userId.");
      }
      return new NotificationProcessingResult(command.eventId(), command.eventName(),
          NotificationProcessingResult.Status.IGNORED);
    }
    email.send(mapper.toEmailDestination(command, type), command.eventId().toString());
    return new NotificationProcessingResult(command.eventId(), command.eventName(),
        NotificationProcessingResult.Status.SENT);
  }
}
