package com.jcaa.notifications;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class NotificationContextTest {
  @Test
  void startsHttpServiceWithoutDatabaseOrUserApiCredentials() {
    new WebApplicationContextRunner()
        .withUserConfiguration(NotificationApplication.class)
        .withPropertyValues(
            "app.kafka.enabled=false",
            "BREVO_API_KEY=fake-test-key",
            "BREVO_FROM_ADDRESS=demo@example.test")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(HealthController.class);
              assertThat(context).doesNotHaveBean("dataSource").doesNotHaveBean("accountService");
            });
  }
}
