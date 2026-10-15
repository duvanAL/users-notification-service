package com.jcaa.notifications.application.model;

public enum NotificationEventType {
  USER_CREATED("user.created"),
  USER_UPDATED("user.updated"),
  USER_DELETED("user.deleted"),
  ACCOUNT_CREATED("account.created"),
  LEGACY_EMAIL_REQUESTED("legacy.email.requested");

  private final String wireName;

  NotificationEventType(String wireName) {
    this.wireName = wireName;
  }

  public static NotificationEventType from(String name) {
    for (NotificationEventType type : values()) {
      if (type.wireName.equals(name)) return type;
    }
    throw new IllegalArgumentException("Unsupported notification event.");
  }
}
