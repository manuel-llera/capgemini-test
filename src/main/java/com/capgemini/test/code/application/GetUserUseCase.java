package com.capgemini.test.code.application;

import com.capgemini.test.code.domain.User;

public interface GetUserUseCase {
  User get(Long userId, Long roomId);
}
