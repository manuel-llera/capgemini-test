package com.capgemini.test.code.domain;

import com.capgemini.test.code.domain.exception.ValidationException;

public record User(
    Long id,
    String name,
    String email,
    String phone,
    Role role,
    String dni,
    Long roomId) {

  public static User create(
      String name, String email, String phone, String role, String dni, Long roomId) {
    validateRequired(name, "userName");
    if (name.length() > 6) {
      throw new ValidationException("userName");
    }
    validateRequired(email, "email");
    if (!email.contains("@") || !email.contains(".")) {
      throw new ValidationException("email");
    }
    validateRequired(phone, "phone");
    validateRequired(dni, "dni");
    if (roomId == null) {
      throw new IllegalArgumentException("roomId must not be null");
    }

    return new User(null, name, email, phone, Role.from(role), dni, roomId);
  }

  private static void validateRequired(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field);
    }
  }
}
