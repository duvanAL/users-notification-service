package com.jcaa.integration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaTransportConfiguration {
  @Value("${KAFKA_BOOTSTRAP_SERVERS:}")
  private String bootstrap;

  @Value("${KAFKA_USERNAME:}")
  private String username;

  @Value("${KAFKA_PASSWORD:}")
  private String password;

  @Value("${KAFKA_SSL_TRUST_CERTIFICATES:}")
  private String certificates;

  @Value("${KAFKA_SECURITY_PROTOCOL:SASL_SSL}")
  private String securityProtocol;

  @Value("${KAFKA_SASL_MECHANISM:SCRAM-SHA-256}")
  private String saslMechanism;

  @Value("${KAFKA_SEND_TIMEOUT_MS:10000}")
  private int sendTimeout;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.core.env.Environment environment;

  private Map<String, Object> common() {
    boolean local =
        environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))
            && "PLAINTEXT".equals(securityProtocol);
    if (bootstrap.isBlank() || (!local && (username.isBlank() || password.isBlank())))
      throw new IllegalStateException("Kafka endpoint and credentials are required.");
    if (!local && !"SASL_SSL".equals(securityProtocol))
      throw new IllegalStateException("Kafka requires SASL_SSL.");
    Map<String, Object> values = new HashMap<>();
    values.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    values.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
    values.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
    values.put(
        SaslConfigs.SASL_JAAS_CONFIG,
        "org.apache.kafka.common.security.scram.ScramLoginModule required username=\""
            + escape(username)
            + "\" password=\""
            + escape(password)
            + "\";");
    values.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
    if (!certificates.isBlank()) {
      values.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
      values.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, certificates.replace("\\n", "\n"));
    }
    return values;
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Bean
  KafkaTemplate<String, String> eventKafkaTemplate() {
    Map<String, Object> values = common();
    values.put(ProducerConfig.ACKS_CONFIG, "all");
    values.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    values.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeout);
    return new KafkaTemplate<>(
        new DefaultKafkaProducerFactory<>(values, new StringSerializer(), new StringSerializer()));
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      @Value("${KAFKA_NOTIFY_GROUP_ID:notify-service}") String group,
      @Value("${KAFKA_AUTO_OFFSET_RESET:earliest}") String reset,
      @Value("${KAFKA_LISTENER_AUTO_STARTUP:true}") boolean startup,
      org.springframework.beans.factory.ObjectProvider<
              org.springframework.kafka.listener.DefaultErrorHandler>
          errors) {
    Map<String, Object> values = common();
    values.put(ConsumerConfig.GROUP_ID_CONFIG, group);
    values.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    values.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, reset);
    values.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(
        new DefaultKafkaConsumerFactory<>(
            values, new StringDeserializer(), new StringDeserializer()));
    factory.setAutoStartup(startup);
    errors.ifAvailable(factory::setCommonErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.getContainerProperties().setAuthExceptionRetryInterval(Duration.ofSeconds(30));
    return factory;
  }
}
