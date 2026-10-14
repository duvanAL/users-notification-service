package com.jcaa.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.DomainEvent;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EventEmailMapper {
  private final ObjectMapper mapper;

  public EventEmailMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Delivery map(String json) {
    try {
      JsonNode node = mapper.readTree(json);
      if (node == null || !node.isObject())
        throw new IllegalArgumentException("Expected an event object.");
      if (!node.has("eventName")) {
        if (node.path("schemaVersion").asInt() != 1)
          throw new IllegalArgumentException("Unsupported legacy event.");
        UUID id = UUID.fromString(node.path("notificationId").asText());
        return new Delivery(
            id,
            "legacy.email.requested",
            new EmailDestinationModel(
                node.path("destinationEmail").asText(), node.path("destinationName").asText(),
                node.path("subject").asText(), node.path("htmlContent").asText()));
      }
      DomainEvent event = mapper.treeToValue(node, DomainEvent.class);
      Map<String, String> payload = event.payload();
      String name = required(payload, "name");
      String email = required(payload, "email");
      required(payload, "userId");
      String subject;
      String body;
      switch (event.eventName()) {
        case "user.created" -> {
          subject = "Bienvenido - Gestion de Usuarios";
          body = "Tu usuario fue creado correctamente.";
        }
        case "user.updated" -> {
          subject = "Usuario actualizado";
          body = "Los datos de tu usuario fueron actualizados.";
        }
        case "account.created" -> {
          UUID.fromString(required(payload, "accountId"));
          String type = required(payload, "accountType");
          if (!java.util.Set.of("SAVINGS", "CHECKING").contains(type))
            throw new IllegalArgumentException("Unsupported account type.");
          subject = "Cuenta creada";
          body = "Tu cuenta " + type + " fue creada correctamente.";
        }
        default -> throw new IllegalArgumentException("Unsupported domain event.");
      }
      return new Delivery(
          event.eventId(),
          event.eventName(),
          new EmailDestinationModel(
              email, name, subject, "<p>Hola " + escape(name) + ".</p><p>" + body + "</p>"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid event JSON.", exception);
    }
  }

  private static String required(Map<String, String> payload, String key) {
    String value = payload.get(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Required event field is missing.");
    return value;
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  public record Delivery(UUID id, String eventName, EmailDestinationModel email) {
    @Override
    public String toString() {
      return "Delivery[id=" + id + ", eventName=" + eventName + "]";
    }
  }
}
