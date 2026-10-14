package com.jcaa.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class ConsumerErrorConfiguration {
  @Bean
  DefaultErrorHandler notificationErrors(NotificationProcessor processor) {
    DefaultErrorHandler handler =
        new DefaultErrorHandler(processor::recover, new FixedBackOff(1000, 2));
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
  }
}
