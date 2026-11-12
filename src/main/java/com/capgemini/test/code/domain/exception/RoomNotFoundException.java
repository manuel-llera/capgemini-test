package com.capgemini.test.code.domain.exception;

public class RoomNotFoundException extends RuntimeException {
  public RoomNotFoundException(Long roomId) {
    super("Room not found: " + roomId);
  }
}
