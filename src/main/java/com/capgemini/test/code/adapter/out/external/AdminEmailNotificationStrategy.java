package com.capgemini.test.code.adapter.out.external;

import org.springframework.stereotype.Component;

import com.capgemini.test.code.domain.Role;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.ExternalServiceException;

import feign.FeignException;

@Component
public class AdminEmailNotificationStrategy implements UserNotificationStrategy {
  private final EmailClient emailClient;

  public AdminEmailNotificationStrategy(EmailClient emailClient) {
    this.emailClient = emailClient;
  }

  @Override
  public Role role() {
    return Role.ADMIN;
  }

  @Override
  public void notifyUser(User user) {
    try {
      emailClient.send(new EmailNotificationRequest(user.email(), "usuario guardado"));
    } catch (FeignException exception) {
      throw new ExternalServiceException("email notification", exception);
    }
  }
}
