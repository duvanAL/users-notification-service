package com.jcaa.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
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
  private final EventEmailMapper mapper;
  private final EmailSenderPort email;
  private final ObjectMapper json;
  private final KafkaTemplate<String, String> kafka;
  private final String resultTopic;
  private final String dlqTopic;
  private final int timeout;

  public NotificationProcessor(
      EventEmailMapper mapper,
      EmailSenderPort email,
      ObjectMapper json,
      KafkaTemplate<String, String> kafka,
      @Value("${KAFKA_TOPIC_NOTIFICATION_RESULT:user.notification.result}") String resultTopic,
      @Value("${KAFKA_TOPIC_NOTIFICATION_DLQ:user.notification.dlq}") String dlqTopic,
      @Value("${KAFKA_SEND_TIMEOUT_MS:10000}") int timeout) {
    this.mapper = mapper;
    this.email = email;
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
    EventEmailMapper.Delivery delivery = mapper.map(raw);
    email.send(delivery.email(), delivery.id().toString());
    outcome(delivery.id(), "SENT", null);
    LOG.info("Notification processed. id={} event={}", delivery.id(), delivery.eventName());
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
      eventId = mapper.map(String.valueOf(record.value())).id();
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
