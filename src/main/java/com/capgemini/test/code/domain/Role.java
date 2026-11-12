package com.capgemini.test.code.domain;

import java.util.Locale;

import com.capgemini.test.code.domain.exception.ValidationException;

public enum Role {
  ADMIN("admin"),
  SUPERADMIN("superadmin");

  private final String value;

  Role(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Role from(String value) {
    if (value != null) {
      String normalized = value.trim().toLowerCase(Locale.ROOT);
      for (Role role : values()) {
        if (role.value.equals(normalized)) {
          return role;
        }
      }
    }
    throw new ValidationException("role");
  }
}
