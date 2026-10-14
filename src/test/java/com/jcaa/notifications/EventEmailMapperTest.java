package com.jcaa.notifications;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.DomainEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEmailMapperTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final EventEmailMapper mapper = new EventEmailMapper(json);

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
      var delivery = mapper.map(json.writeValueAsString(event));
      assertThat(delivery.id()).isEqualTo(event.eventId());
      assertThat(delivery.email().getBody()).contains("&lt;Demo&gt;").doesNotContain("<Demo>");
    }
  }

  @Test
  void rejectsUnknownEventsInvalidSchemasAndMissingData() {
    assertThatThrownBy(() -> mapper.map("{}")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map("null")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map("{")).isInstanceOf(IllegalArgumentException.class);
    for (String type : new String[] {"unknown.event", "user.created"}) {
      assertThatThrownBy(
              () -> mapper.map(json.writeValueAsString(DomainEvent.create(type, Map.of()))))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void readsPendingLegacyRequestsWithoutChangingTheirStableId() {
    var delivery =
        mapper.map(
            """
{"schemaVersion":1,"notificationId":"00000000-0000-0000-0000-000000000002",
 "destinationEmail":"demo@example.test","destinationName":"Demo","subject":"Legacy","htmlContent":"<p>Hello</p>"}
""");
    assertThat(delivery.id().toString()).endsWith("0002");
    assertThat(delivery.email().getSubject()).isEqualTo("Legacy");
  }
}
