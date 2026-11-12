package com.capgemini.test.code.application;

public interface CreateUserUseCase {
  Long create(CreateUserCommand command, Long roomId);
}
