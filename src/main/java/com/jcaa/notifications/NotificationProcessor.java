package com.jcaa.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.notifications.application.dto.ProcessNotificationCommand;
import com.jcaa.notifications.application.model.NotificationProcessingResult;
import com.jcaa.notifications.application.port.in.ProcessNotificationEventUseCase;
import com.jcaa.notifications.infrastructure.entrypoint.kafka.NotificationEventRequestMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class NotificationProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(NotificationProcessor.class);
  private final NotificationEventRequestMapper mapper;
  private final ProcessNotificationEventUseCase useCase;
  private final ObjectMapper json;
  private final KafkaTemplate<String, String> kafka;
  private final String resultTopic;
  private final String dlqTopic;
  private final int timeout;

  public NotificationProcessor(
      NotificationEventRequestMapper mapper,
      ProcessNotificationEventUseCase useCase,
      ObjectMapper json,
      KafkaTemplate<String, String> kafka,
      @Value("${KAFKA_TOPIC_NOTIFICATION_RESULT:user.notification.result}") String resultTopic,
      @Value("${KAFKA_TOPIC_NOTIFICATION_DLQ:user.notification.dlq}") String dlqTopic,
      @Value("${KAFKA_SEND_TIMEOUT_MS:10000}") int timeout) {
    this.mapper = mapper;
    this.useCase = useCase;
    this.json = json;
    this.kafka = kafka;
    this.resultTopic = resultTopic;
    this.dlqTopic = dlqTopic;
    this.timeout = timeout;
  }

  @KafkaListener(
      topics = "${KAFKA_TOPIC_NOTIFICATION_REQUESTED:user.notification.requested}",
      groupId = "${KAFKA_NOTIFY_GROUP_ID:notify-service}")
  public void process(String raw) {
    ProcessNotificationCommand command = mapper.toCommand(raw);
    NotificationProcessingResult result = useCase.process(command);
    if (result.status() == NotificationProcessingResult.Status.IGNORED) {
      LOG.info("Notification intentionally ignored. id={} event={}", result.eventId(), result.eventName());
      return; // No provider call, SENT, FAILED or DLQ for a valid deleted event.
    }
    outcome(result.eventId(), "SENT", null);
    LOG.info("Notification processed. id={} event={}", result.eventId(), result.eventName());
  }

  public void recover(ConsumerRecord<?, ?> record, Exception exception) {
    // Only safe metadata reaches this DLQ, never raw payloads or provider error bodies.
    publish(
        dlqTopic,
        record.topic() + ":" + record.partition() + ":" + record.offset(),
        Map.of(
            "schemaVersion",
            1,
            "sourceTopic",
            record.topic(),
            "sourcePartition",
            record.partition(),
            "sourceOffset",
            record.offset(),
            "failureCode",
            "PROCESSING_RETRIES_EXHAUSTED",
            "failedAt",
            Instant.now()));
    UUID eventId = null;
    try {
      eventId = mapper.toCommand(String.valueOf(record.value())).eventId();
    } catch (RuntimeException ignored) {
      /* malformed payload has no trusted correlation id */
    }
    if (eventId != null) outcome(eventId, "FAILED", "PROCESSING_RETRIES_EXHAUSTED");
    LOG.warn(
        "Notification rejected after retries. topic={} partition={} offset={}",
        record.topic(),
        record.partition(),
        record.offset());
  }

  private void outcome(UUID id, String status, String failure) {
    publish(resultTopic, id.toString(), new Outcome(1, id, status, Instant.now(), failure));
  }

  private void publish(String topic, String key, Object value) {
    try {
      kafka.send(topic, key, json.writeValueAsString(value)).get(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kafka send interrupted.", e);
    } catch (Exception e) {
      throw new IllegalStateException("Kafka publish failed.", e);
    }
  }

  public record Outcome(
      int schemaVersion,
      UUID notificationId,
      String status,
      Instant processedAt,
      String failureCode) {}
}
