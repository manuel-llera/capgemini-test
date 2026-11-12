package com.capgemini.test.code.adapter.out.external;

import com.capgemini.test.code.domain.Role;
import com.capgemini.test.code.domain.User;

public interface UserNotificationStrategy {
  Role role();

  void notifyUser(User user);
}
