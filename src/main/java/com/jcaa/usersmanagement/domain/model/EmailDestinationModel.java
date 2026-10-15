package com.jcaa.usersmanagement.domain.model;

import com.jcaa.usersmanagement.domain.exception.InvalidEmailDestinationException;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import lombok.Value;

@Value
@lombok.ToString(onlyExplicitlyIncluded = true)
public class EmailDestinationModel {

  String destinationEmail;
  String destinationName;
  String subject;
  String body;

  public EmailDestinationModel(
      final String destinationEmail,
      final String destinationName,
      final String subject,
      final String body) {
    this.destinationEmail = new UserEmail(destinationEmail).value();
    this.destinationName = validateNotBlank(destinationName);
    this.subject = validateNotBlank(subject);
    this.body = validateNotBlank(body);
  }

  private static String validateNotBlank(final String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidEmailDestinationException();
    }
    return value;
  }
}
