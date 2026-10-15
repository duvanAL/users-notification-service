package com.jcaa.notifications.infrastructure.entrypoint.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.DomainEvent;
import com.jcaa.notifications.application.dto.ProcessNotificationCommand;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Kafka wire DTO to application command; contains no email orchestration. */
@Component
public class NotificationEventRequestMapper {
  private final ObjectMapper mapper;

  public NotificationEventRequestMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public ProcessNotificationCommand toCommand(String raw) {
    try {
      JsonNode node = mapper.readTree(raw);
      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException("Expected an event object.");
      }
      if (!node.has("eventName")) {
        if (node.path("schemaVersion").asInt() != 1) {
          throw new IllegalArgumentException("Unsupported legacy event.");
        }
        Instant occurred = node.hasNonNull("occurredAt")
            ? Instant.parse(node.path("occurredAt").asText()) : Instant.EPOCH;
        return new ProcessNotificationCommand(
            UUID.fromString(node.path("notificationId").asText()), "legacy.email.requested", occurred,
            Map.of("email", node.path("destinationEmail").asText(),
                "name", node.path("destinationName").asText(),
                "subject", node.path("subject").asText(),
                "htmlContent", node.path("htmlContent").asText()));
      }
      DomainEvent event = mapper.treeToValue(node, DomainEvent.class);
      return new ProcessNotificationCommand(event.eventId(), event.eventName(),
          event.occurredOn(), event.payload());
    } catch (JsonProcessingException | IllegalArgumentException | DateTimeException exception) {
      throw new IllegalArgumentException("Invalid notification event.");
    }
  }
}
