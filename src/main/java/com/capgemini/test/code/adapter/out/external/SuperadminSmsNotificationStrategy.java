package com.capgemini.test.code.adapter.out.external;

import org.springframework.stereotype.Component;

import com.capgemini.test.code.domain.Role;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.ExternalServiceException;

import feign.FeignException;

@Component
public class SuperadminSmsNotificationStrategy implements UserNotificationStrategy {
  private final SmsClient smsClient;

  public SuperadminSmsNotificationStrategy(SmsClient smsClient) {
    this.smsClient = smsClient;
  }

  @Override
  public Role role() {
    return Role.SUPERADMIN;
  }

  @Override
  public void notifyUser(User user) {
    try {
      smsClient.send(new SmsNotificationRequest(user.phone(), "usuario guardado"));
    } catch (FeignException exception) {
      throw new ExternalServiceException("SMS notification", exception);
    }
  }
}
