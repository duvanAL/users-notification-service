package com.jcaa.events;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Version 1 wire contract shared by independently deployed producers and consumers. */
public record DomainEvent(
    int schemaVersion,
    UUID eventId,
    String eventName,
    Instant occurredOn,
    Map<String, String> payload) {
  public DomainEvent {
    if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported event schema.");
    Objects.requireNonNull(eventId, "eventId is required.");
    Objects.requireNonNull(occurredOn, "occurredOn is required.");
    if (eventName == null || eventName.isBlank())
      throw new IllegalArgumentException("eventName is required.");
    payload = Map.copyOf(Objects.requireNonNull(payload, "payload is required."));
  }

  public static DomainEvent create(String name, Map<String, String> payload) {
    return new DomainEvent(1, UUID.randomUUID(), name, Instant.now(), payload);
  }

  @Override
  public String toString() {
    return "DomainEvent[eventId=" + eventId + ", eventName=" + eventName + "]";
  }
}
