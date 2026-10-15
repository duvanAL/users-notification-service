package com.jcaa.usersmanagement.domain.exception;

/** Deliberately excludes the rejected address from diagnostics. */
public final class InvalidUserEmailException extends IllegalArgumentException {
  public InvalidUserEmailException() {
    super("Invalid recipient email.");
  }
}
