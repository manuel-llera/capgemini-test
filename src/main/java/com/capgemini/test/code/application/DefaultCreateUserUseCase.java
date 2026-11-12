package com.capgemini.test.code.application;

import org.springframework.stereotype.Service;

import com.capgemini.test.code.application.port.DniValidator;
import com.capgemini.test.code.application.port.NotificationPort;
import com.capgemini.test.code.application.port.UserRepository;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.DuplicateEmailException;

@Service
public class DefaultCreateUserUseCase implements CreateUserUseCase {
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
    notificationPort.notifyUser(savedUser);
    return savedUser.id();
  }
}
