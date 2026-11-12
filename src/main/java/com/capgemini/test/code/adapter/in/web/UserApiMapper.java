package com.capgemini.test.code.adapter.in.web;

import org.springframework.stereotype.Component;

import com.capgemini.test.code.application.CreateUserCommand;
import com.capgemini.test.code.domain.User;

@Component
public class UserApiMapper {
  public CreateUserCommand toCommand(CreateUserRequest request) {
    return new CreateUserCommand(
        request.name(), request.email(), request.phone(), request.role(), request.dni());
  }

  public UserResponse toResponse(User user) {
    return UserResponse.from(user);
  }
}
