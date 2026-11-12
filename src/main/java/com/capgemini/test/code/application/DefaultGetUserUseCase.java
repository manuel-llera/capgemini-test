package com.capgemini.test.code.application;

import org.springframework.stereotype.Service;

import com.capgemini.test.code.application.port.UserRepository;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.UserNotFoundException;

@Service
public class DefaultGetUserUseCase implements GetUserUseCase {
  private final UserRepository userRepository;

  public DefaultGetUserUseCase(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public User get(Long userId, Long roomId) {
    return userRepository
        .findByIdAndRoomId(userId, roomId)
        .orElseThrow(() -> new UserNotFoundException(userId));
  }
}
