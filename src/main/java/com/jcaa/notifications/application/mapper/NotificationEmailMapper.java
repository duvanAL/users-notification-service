package com.jcaa.notifications.application.mapper;

import com.jcaa.notifications.application.dto.ProcessNotificationCommand;
import com.jcaa.notifications.application.model.NotificationEventType;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationEmailMapper {
  public EmailDestinationModel toEmailDestination(
      ProcessNotificationCommand command, NotificationEventType type) {
    Map<String, String> payload = command.payload();
    String name = required(payload, "name");
    String email = required(payload, "email");
    if (type == NotificationEventType.LEGACY_EMAIL_REQUESTED) {
      return new EmailDestinationModel(email, name, required(payload, "subject"),
          required(payload, "htmlContent"));
    }
    required(payload, "userId");
    String subject;
    String body;
    switch (type) {
      case USER_CREATED -> {
        subject = "Bienvenido - Gestion de Usuarios";
        body = "Tu usuario fue creado correctamente.";
      }
      case USER_UPDATED -> {
        subject = "Usuario actualizado";
        body = "Los datos de tu usuario fueron actualizados.";
      }
      case ACCOUNT_CREATED -> {
        UUID.fromString(required(payload, "accountId"));
        String accountType = required(payload, "accountType");
        if (!Set.of("SAVINGS", "CHECKING").contains(accountType)) {
          throw new IllegalArgumentException("Unsupported account type.");
        }
        subject = "Cuenta creada";
        body = "Tu cuenta " + accountType + " fue creada correctamente.";
      }
      default -> throw new IllegalArgumentException("Event does not produce an email.");
    }
    return new EmailDestinationModel(email, name, subject,
        "<p>Hola " + escape(name) + ".</p><p>" + body + "</p>");
  }

  private static String required(Map<String, String> payload, String key) {
    String value = payload.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Required notification field is missing.");
    }
    return value;
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }
}
