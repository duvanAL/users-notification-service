package com.jcaa.usersmanagement.domain.exception;

public final class InvalidEmailDestinationException extends IllegalArgumentException {
  public InvalidEmailDestinationException() {
    super("Invalid email destination.");
  }
}
