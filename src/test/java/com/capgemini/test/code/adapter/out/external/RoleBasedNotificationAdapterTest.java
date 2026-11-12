package com.capgemini.test.code.adapter.out.external;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.capgemini.test.code.domain.Role;
import com.capgemini.test.code.domain.User;

class RoleBasedNotificationAdapterTest {
  private final EmailClient emailClient = mock(EmailClient.class);
  private final SmsClient smsClient = mock(SmsClient.class);
  private final UserNotificationStrategy adminStrategy =
      new AdminEmailNotificationStrategy(emailClient);
  private final UserNotificationStrategy superadminStrategy =
      new SuperadminSmsNotificationStrategy(smsClient);
  private final RoleBasedNotificationAdapter adapter =
      new RoleBasedNotificationAdapter(List.of(adminStrategy, superadminStrategy));

  @Test
  void adminReceivesEmailWithExpectedMessage() {
    User user = user(Role.ADMIN, "admin@example.com", "600000001");

    adapter.notifyUser(user);

    verify(emailClient).send(new EmailNotificationRequest("admin@example.com", "usuario guardado"));
  }

  @Test
  void superadminReceivesSmsWithExpectedMessage() {
    User user = user(Role.SUPERADMIN, "superadmin@example.com", "600000002");

    adapter.notifyUser(user);

    verify(smsClient).send(new SmsNotificationRequest("600000002", "usuario guardado"));
  }

  private User user(Role role, String email, String phone) {
    return new User(1L, "pablo", email, phone, role, "12345678A", 1L);
  }
}
