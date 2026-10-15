package com.jcaa.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jcaa.notifications.application.dto.ProcessNotificationCommand;
import com.jcaa.notifications.application.mapper.NotificationEmailMapper;
import com.jcaa.notifications.application.service.NotificationEventApplicationService;
import com.jcaa.usersmanagement.domain.exception.InvalidEmailDestinationException;
import com.jcaa.usersmanagement.domain.exception.InvalidUserEmailException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import com.jcaa.usersmanagement.domain.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDomainTest {
  @Test
  void normalizesEmailWithoutLeakingItInDiagnostics() {
    UserEmail email = new UserEmail("  DEMO@Example.TEST  ");
    assertThat(email.value()).isEqualTo("demo@example.test");
    assertThat(email.toString()).doesNotContain("demo@");
    assertThat(new EmailDestinationModel(email.value(), "Demo", "Subject", "Body").toString())
        .doesNotContain("demo@", "Subject", "Body");
  }

  @Test
  void rejectsInvalidEmailBeforeProviderInvocation() {
    for (String invalid : new String[] {null, "", " ", "not-email", "a@", "@example.test",
        "a..b@example.test", ".a@example.test", "a.@example.test", "a@-example.test",
        "a@example..test", "a@localhost", "a@exam ple.test", "a\r\n@example.test"}) {
      assertThatThrownBy(() -> new UserEmail(invalid)).isInstanceOf(InvalidUserEmailException.class)
          .hasMessage("Invalid recipient email.");
    }
  }

  @Test
  void requiresACompleteEmailDestination() {
    assertThatThrownBy(() -> new EmailDestinationModel("demo@example.test", "", "Subject", "Body"))
        .isInstanceOf(InvalidEmailDestinationException.class);
    assertThatThrownBy(() -> new EmailDestinationModel("demo@example.test", "Demo", null, "Body"))
        .isInstanceOf(InvalidEmailDestinationException.class);
  }

  @Test
  void commandCopiesItsPayloadAndRejectsMissingIdentity() {
    Map<String, String> payload = new HashMap<>(Map.of("userId", "u-1"));
    var command = new ProcessNotificationCommand(UUID.randomUUID(), "user.deleted", Instant.now(), payload);
    payload.put("userId", "changed");
    assertThat(command.payload()).containsEntry("userId", "u-1");
    assertThatThrownBy(() -> command.payload().put("userId", "x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new ProcessNotificationCommand(null, "user.created", Instant.now(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unsupportedOrIncompleteCommandsNeverSend() {
    EmailSenderPort email = mock(EmailSenderPort.class);
    var service = new NotificationEventApplicationService(email, new NotificationEmailMapper());
    assertThatThrownBy(() -> service.process(null)).isInstanceOf(IllegalArgumentException.class);
    for (String type : new String[] {"unknown.event", "user.created", "user.deleted"}) {
      assertThatThrownBy(() -> service.process(new ProcessNotificationCommand(
          UUID.randomUUID(), type, Instant.now(), Map.of())))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(email);
  }
}
