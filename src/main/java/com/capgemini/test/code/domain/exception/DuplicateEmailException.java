package com.capgemini.test.code.domain.exception;

public class DuplicateEmailException extends ValidationException {
  public DuplicateEmailException() {
    super("email");
  }
}
