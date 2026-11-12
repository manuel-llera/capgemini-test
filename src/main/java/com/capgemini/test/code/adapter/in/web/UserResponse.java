package com.capgemini.test.code.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.capgemini.test.code.domain.User;

public record UserResponse(
    Long id,
    String name,
    String email,
    String phone,
    @JsonProperty("rol") String role,
    String dni) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.id(), user.name(), user.email(), user.phone(), user.role().value(), user.dni());
  }
}
