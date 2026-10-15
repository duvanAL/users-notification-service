package com.jcaa.notifications;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.DomainEvent;
import com.jcaa.notifications.application.mapper.NotificationEmailMapper;
import com.jcaa.notifications.application.model.NotificationEventType;
import com.jcaa.notifications.infrastructure.entrypoint.kafka.NotificationEventRequestMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEmailMapperTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final NotificationEventRequestMapper requests = new NotificationEventRequestMapper(json);
  private final NotificationEmailMapper mapper = new NotificationEmailMapper();

  @Test
  void mapsBothProducerEventsAndEscapesRecipientName() throws Exception {
    for (String type : new String[] {"user.created", "user.updated", "account.created"}) {
      DomainEvent event =
          DomainEvent.create(
              type,
              Map.of(
                  "userId",
                  "usr-1",
                  "name",
                  "<Demo>",
                  "email",
                  "demo@example.test",
                  "accountId",
                  "00000000-0000-0000-0000-000000000001",
                  "accountType",
                  "SAVINGS"));
      var command = requests.toCommand(json.writeValueAsString(event));
      var destination = mapper.toEmailDestination(command, NotificationEventType.from(command.eventName()));
      assertThat(command.eventId()).isEqualTo(event.eventId());
      assertThat(destination.getBody()).contains("&lt;Demo&gt;").doesNotContain("<Demo>");
    }
  }

  @Test
  void rejectsUnknownEventsInvalidSchemasAndMissingData() {
    assertThatThrownBy(() -> requests.toCommand("{}")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> requests.toCommand("null")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> requests.toCommand("{")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> requests.toCommand(
        "{\"schemaVersion\":1,\"occurredAt\":\"private@example.test\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid notification event.")
        .hasNoCause();
    for (String type : new String[] {"unknown.event", "user.created"}) {
      assertThatThrownBy(
              () -> {
                var command = requests.toCommand(json.writeValueAsString(DomainEvent.create(type, Map.of())));
                mapper.toEmailDestination(command, NotificationEventType.from(command.eventName()));
              })
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void readsPendingLegacyRequestsWithoutChangingTheirStableId() {
    var delivery =
        requests.toCommand(
            """
{"schemaVersion":1,"notificationId":"00000000-0000-0000-0000-000000000002",
 "destinationEmail":"demo@example.test","destinationName":"Demo","subject":"Legacy","htmlContent":"<p>Hello</p>"}
""");
    assertThat(delivery.eventId().toString()).endsWith("0002");
    assertThat(mapper.toEmailDestination(delivery, NotificationEventType.LEGACY_EMAIL_REQUESTED)
        .getSubject()).isEqualTo("Legacy");
  }
}
