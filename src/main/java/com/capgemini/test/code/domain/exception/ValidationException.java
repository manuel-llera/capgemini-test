package com.capgemini.test.code.domain.exception;

public class ValidationException extends RuntimeException {
  private final String field;

  public ValidationException(String field) {
    super("Invalid " + field);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
