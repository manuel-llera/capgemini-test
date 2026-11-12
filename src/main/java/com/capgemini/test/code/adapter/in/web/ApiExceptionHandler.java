package com.capgemini.test.code.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.capgemini.test.code.domain.exception.ExternalServiceException;
import com.capgemini.test.code.domain.exception.RoomNotFoundException;
import com.capgemini.test.code.domain.exception.UserNotFoundException;
import com.capgemini.test.code.domain.exception.ValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ApiError> handleValidation(ValidationException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError(HttpStatus.CONFLICT.value(), "error validation " + exception.getField()));
  }

  @ExceptionHandler({UserNotFoundException.class, RoomNotFoundException.class})
  public ResponseEntity<ApiError> handleNotFound(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiError(HttpStatus.NOT_FOUND.value(), exception.getMessage()));
  }

  @ExceptionHandler(ExternalServiceException.class)
  public ResponseEntity<ApiError> handleExternalService(ExternalServiceException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new ApiError(HttpStatus.BAD_GATEWAY.value(), "external service unavailable"));
  }
}
