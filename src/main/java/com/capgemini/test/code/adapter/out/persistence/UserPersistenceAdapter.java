package com.capgemini.test.code.adapter.out.persistence;

import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.capgemini.test.code.application.port.UserRepository;
import com.capgemini.test.code.domain.Role;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.RoomNotFoundException;
import com.capgemini.test.code.domain.exception.ValidationException;

@Repository
public class UserPersistenceAdapter implements UserRepository {
  private final UserJpaRepository userRepository;
  private final RoomJpaRepository roomRepository;

  public UserPersistenceAdapter(
      UserJpaRepository userRepository, RoomJpaRepository roomRepository) {
    this.userRepository = userRepository;
    this.roomRepository = roomRepository;
  }

  @Override
  public boolean existsByEmail(String email) {
    return userRepository.existsByEmail(email);
  }

  @Override
  @Transactional
  public User save(User user) {
    RoomEntity room =
        roomRepository
            .findById(user.roomId())
            .orElseThrow(() -> new RoomNotFoundException(user.roomId()));

    UserEntity entity = new UserEntity();
    entity.setName(user.name());
    entity.setEmail(user.email());
    entity.setDni(user.dni());
    entity.setPhone(user.phone());
    entity.setRole(user.role().value());
    entity.setRoom(room);

    try {
      return toDomain(userRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException exception) {
      String field = uniqueConstraintField(exception);
      if (field == null) {
        throw exception;
      }
      throw new ValidationException(field);
    }
  }

  @Override
  public Optional<User> findByIdAndRoomId(Long userId, Long roomId) {
    return userRepository.findByIdAndRoom_Id(userId, roomId).map(this::toDomain);
  }

  private User toDomain(UserEntity entity) {
    return new User(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getPhone(),
        Role.from(entity.getRole()),
        entity.getDni(),
        entity.getRoom().getId());
  }

  private String uniqueConstraintField(DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException violation) {
        String constraint = violation.getConstraintName();
        if (constraint != null) {
          String normalized = constraint.toLowerCase(Locale.ROOT);
          if (normalized.contains("email")) {
            return "email";
          }
          if (normalized.contains("dni")) {
            return "dni";
          }
          if (normalized.contains("phone")) {
            return "phone";
          }
        }
        return null;
      }
      cause = cause.getCause();
    }
    return null;
  }
}
