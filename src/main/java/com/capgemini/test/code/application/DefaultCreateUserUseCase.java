package com.capgemini.test.code.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.capgemini.test.code.application.port.DniValidator;
import com.capgemini.test.code.application.port.NotificationPort;
import com.capgemini.test.code.application.port.UserRepository;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.DuplicateEmailException;
import com.capgemini.test.code.domain.exception.ExternalServiceException;

@Service
public class DefaultCreateUserUseCase implements CreateUserUseCase {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCreateUserUseCase.class);

  private final UserRepository userRepository;
  private final DniValidator dniValidator;
  private final NotificationPort notificationPort;

  public DefaultCreateUserUseCase(
      UserRepository userRepository,
      DniValidator dniValidator,
      NotificationPort notificationPort) {
    this.userRepository = userRepository;
    this.dniValidator = dniValidator;
    this.notificationPort = notificationPort;
  }

  @Override
  public Long create(CreateUserCommand command, Long roomId) {
    User user =
        User.create(
            command.name(),
            command.email(),
            command.phone(),
            command.role(),
            command.dni(),
            roomId);

    if (userRepository.existsByEmail(user.email())) {
      throw new DuplicateEmailException();
    }

    dniValidator.validate(user.dni());
    User savedUser = userRepository.save(user);
    try {
      notificationPort.notifyUser(savedUser);
    } catch (ExternalServiceException exception) {
      boolean persisted =
          userRepository.findByIdAndRoomId(savedUser.id(), roomId).isPresent();
      if (!persisted) {
        throw exception;
      }
      LOGGER.warn(
          "Notification failed for persisted user id {}; returning the created user id",
          savedUser.id(),
          exception);
    }
    return savedUser.id();
  }
}
