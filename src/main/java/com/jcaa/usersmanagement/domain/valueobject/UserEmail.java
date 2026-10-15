package com.jcaa.usersmanagement.domain.valueobject;

import com.jcaa.usersmanagement.domain.exception.InvalidUserEmailException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Application-supported address syntax, not a claim of mailbox existence. */
public record UserEmail(String value) {
  private static final Pattern EMAIL = Pattern.compile(
      "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
          + "(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$");

  public UserEmail {
    if (value == null) throw new InvalidUserEmailException();
    value = value.trim().toLowerCase(Locale.ROOT);
    int at = value.indexOf('@');
    if (value.length() > 254 || at < 1 || at > 64 || !EMAIL.matcher(value).matches()
        || value.startsWith(".") || value.charAt(at - 1) == '.'
        || value.substring(0, at).contains("..")) {
      throw new InvalidUserEmailException();
    }
  }

  @Override
  public String toString() {
    return "UserEmail[redacted]";
  }
}
