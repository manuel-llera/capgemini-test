package com.capgemini.test.code.application.port;

import com.capgemini.test.code.domain.User;

public interface NotificationPort {
  void notifyUser(User user);
}
