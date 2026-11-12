package com.capgemini.test.code.domain.exception;

public class ExternalServiceException extends RuntimeException {
  public ExternalServiceException(String service, Throwable cause) {
    super("External service failed: " + service, cause);
  }
}
