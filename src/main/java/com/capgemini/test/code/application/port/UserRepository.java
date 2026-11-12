package com.capgemini.test.code.application.port;

import java.util.Optional;

import com.capgemini.test.code.domain.User;

public interface UserRepository {
  boolean existsByEmail(String email);

  User save(User user);

  Optional<User> findByIdAndRoomId(Long userId, Long roomId);
}
