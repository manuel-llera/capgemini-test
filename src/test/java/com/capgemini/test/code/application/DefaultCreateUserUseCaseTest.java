package com.capgemini.test.code.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capgemini.test.code.application.port.DniValidator;
import com.capgemini.test.code.application.port.NotificationPort;
import com.capgemini.test.code.application.port.UserRepository;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.DuplicateEmailException;
import com.capgemini.test.code.domain.exception.ExternalServiceException;
import com.capgemini.test.code.domain.exception.InvalidDniException;

@ExtendWith(MockitoExtension.class)
class DefaultCreateUserUseCaseTest {
  @Mock private UserRepository userRepository;
  @Mock private DniValidator dniValidator;
  @Mock private NotificationPort notificationPort;
  @InjectMocks private DefaultCreateUserUseCase useCase;

  @Test
  void validatesPersistsAndNotifiesTheSavedUser() {
    CreateUserCommand command =
        new CreateUserCommand("pablo", "pablo@example.com", "600000001", "admin", "12345678A");
    when(userRepository.existsByEmail(command.email())).thenReturn(false);
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 42L));

    Long id = useCase.create(command, 1L);

    assertThat(id).isEqualTo(42L);
    verify(dniValidator).validate(command.dni());
    verify(notificationPort).notifyUser(
        new User(42L, "pablo", "pablo@example.com", "600000001",
            com.capgemini.test.code.domain.Role.ADMIN, "12345678A", 1L));
  }

  @Test
  void duplicateEmailIsRejectedBeforeExternalValidationOrPersistence() {
    CreateUserCommand command =
        new CreateUserCommand("pablo", "existing@example.com", "600000001", "admin", "12345678A");
    when(userRepository.existsByEmail(command.email())).thenReturn(true);

    assertThatThrownBy(() -> useCase.create(command, 1L))
        .isInstanceOf(DuplicateEmailException.class);

    verify(dniValidator, never()).validate(any());
    verify(userRepository, never()).save(any());
    verify(notificationPort, never()).notifyUser(any());
  }

  @Test
  void invalidDniPreventsPersistenceAndNotification() {
    CreateUserCommand command =
        new CreateUserCommand("pablo", "pablo@example.com", "600000001", "admin", "99999999w");
    when(userRepository.existsByEmail(command.email())).thenReturn(false);
    org.mockito.Mockito.doThrow(new InvalidDniException()).when(dniValidator).validate(command.dni());

    assertThatThrownBy(() -> useCase.create(command, 1L))
        .isInstanceOf(InvalidDniException.class);

    verify(userRepository, never()).save(any());
    verify(notificationPort, never()).notifyUser(any());
  }

  @Test
  void returnsCreatedIdWhenNotificationFailsButUserIsConfirmedPersisted() {
    CreateUserCommand command =
        new CreateUserCommand("pablo", "pablo@example.com", "600000001", "admin", "12345678A");
    User savedUser =
        new User(42L, "pablo", command.email(), command.phone(),
            com.capgemini.test.code.domain.Role.ADMIN, command.dni(), 1L);
    when(userRepository.existsByEmail(command.email())).thenReturn(false);
    when(userRepository.save(any(User.class))).thenReturn(savedUser);
    org.mockito.Mockito.doThrow(new ExternalServiceException("email notification", new RuntimeException()))
        .when(notificationPort)
        .notifyUser(savedUser);
    when(userRepository.findByIdAndRoomId(42L, 1L)).thenReturn(Optional.of(savedUser));

    assertThat(useCase.create(command, 1L)).isEqualTo(42L);

    verify(userRepository).findByIdAndRoomId(42L, 1L);
  }

  @Test
  void propagatesNotificationFailureWhenPersistenceCannotBeConfirmed() {
    CreateUserCommand command =
        new CreateUserCommand("pablo", "pablo@example.com", "600000001", "admin", "12345678A");
    User savedUser =
        new User(42L, "pablo", command.email(), command.phone(),
            com.capgemini.test.code.domain.Role.ADMIN, command.dni(), 1L);
    ExternalServiceException notificationFailure =
        new ExternalServiceException("email notification", new RuntimeException());
    when(userRepository.existsByEmail(command.email())).thenReturn(false);
    when(userRepository.save(any(User.class))).thenReturn(savedUser);
    org.mockito.Mockito.doThrow(notificationFailure).when(notificationPort).notifyUser(savedUser);
    when(userRepository.findByIdAndRoomId(42L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.create(command, 1L))
        .isSameAs(notificationFailure);
  }

  private User withId(User user, Long id) {
    return new User(id, user.name(), user.email(), user.phone(), user.role(), user.dni(), user.roomId());
  }
}
