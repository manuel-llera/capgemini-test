package com.capgemini.test.code.domain.exception;

public class InvalidDniException extends ValidationException {
  public InvalidDniException() {
    super("dni");
  }
}
