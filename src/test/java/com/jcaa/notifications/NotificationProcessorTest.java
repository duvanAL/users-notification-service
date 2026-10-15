package com.jcaa.notifications;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.DomainEvent;
import com.jcaa.usersmanagement.domain.port.out.EmailSenderPort;
import com.jcaa.notifications.application.mapper.NotificationEmailMapper;
import com.jcaa.notifications.application.service.NotificationEventApplicationService;
import com.jcaa.notifications.infrastructure.entrypoint.kafka.NotificationEventRequestMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class NotificationProcessorTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final EmailSenderPort email = mock(EmailSenderPort.class);

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

  private NotificationProcessor processor() {
    return new NotificationProcessor(
        new NotificationEventRequestMapper(json),
        new NotificationEventApplicationService(email, new NotificationEmailMapper()),
        json, kafka, "result", "dlq", 1000);
  }

  @Test
  void bothPublishersUseStableIdsAndCompatibleOutcomes() throws Exception {
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    for (String eventName : new String[] {"user.created", "user.updated", "account.created"}) {
      DomainEvent event =
          DomainEvent.create(
              eventName,
              Map.of(
                  "userId",
                  "usr-1",
                  "name",
                  "Demo",
                  "email",
                  "demo@example.test",
                  "accountId",
                  "00000000-0000-0000-0000-000000000001",
                  "accountType",
                  "SAVINGS"));
      processor().process(json.writeValueAsString(event));
      verify(email).send(any(), eq(event.eventId().toString()));
      verify(kafka)
          .send(
              eq("result"),
              eq(event.eventId().toString()),
              argThat(s -> s.contains("\"status\":\"SENT\"")));
    }
  }

  @Test
  void deletionIsAcknowledgedWithoutEmailOrFalseSentResult() throws Exception {
    processor().process(json.writeValueAsString(
        DomainEvent.create("user.deleted", Map.of("userId", "usr-1"))));
    verifyNoInteractions(email, kafka);
  }

  @Test
  void invalidEmailNeverReachesProviderOrSentTopic() throws Exception {
    String raw = json.writeValueAsString(DomainEvent.create("user.created",
        Map.of("userId", "usr-1", "name", "Demo", "email", "not-an-email")));
    assertThatThrownBy(() -> processor().process(raw))
        .isInstanceOf(com.jcaa.usersmanagement.domain.exception.InvalidUserEmailException.class)
        .hasMessage("Invalid recipient email.");
    verifyNoInteractions(email, kafka);
  }

  @Test
  void brokerFailureDoesNotSilentlyAcknowledgeTheRequest() throws Exception {
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));
    String event =
        json.writeValueAsString(
            DomainEvent.create(
                "user.created",
                Map.of("userId", "usr-1", "name", "Demo", "email", "demo@example.test")));
    assertThatThrownBy(() -> processor().process(event)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void malformedMessagesGoToMetadataOnlyDlq() {
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    processor()
        .recover(
            new ConsumerRecord<>("requests", 0, 7L, "key", "private-email@example.test"),
            new IllegalArgumentException());
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(kafka).send(eq("dlq"), eq("requests:0:7"), body.capture());
    assertThat(body.getValue())
        .contains("PROCESSING_RETRIES_EXHAUSTED")
        .doesNotContain("private-email");
    verifyNoInteractions(email);
  }
}
