package com.capgemini.test.code.adapter.out.external;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.capgemini.test.code.application.port.NotificationPort;
import com.capgemini.test.code.domain.Role;
import com.capgemini.test.code.domain.User;

@Component
public class RoleBasedNotificationAdapter implements NotificationPort {
  private final Map<Role, UserNotificationStrategy> strategies;

  public RoleBasedNotificationAdapter(List<UserNotificationStrategy> strategies) {
    this.strategies = new EnumMap<>(Role.class);
    for (UserNotificationStrategy strategy : strategies) {
      UserNotificationStrategy previous = this.strategies.put(strategy.role(), strategy);
      if (previous != null) {
        throw new IllegalStateException("Multiple notification strategies for " + strategy.role());
      }
    }
  }

  @Override
  public void notifyUser(User user) {
    UserNotificationStrategy strategy = strategies.get(user.role());
    if (strategy == null) {
      throw new IllegalStateException("No notification strategy for " + user.role());
    }
    strategy.notifyUser(user);
  }
}
