package com.capgemini.test.code.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.capgemini.test.code.domain.exception.ValidationException;

class UserTest {
  @Test
  void acceptsNameWithSixCharacters() {
    User user = User.create("123456", "a@b.c", "600000001", "admin", "12345678A", 1L);

    assertThat(user.name()).isEqualTo("123456");
  }

  @Test
  void rejectsNameLongerThanSixCharacters() {
    assertThatThrownBy(() -> User.create("1234567", "a@b.c", "600000001", "admin", "12345678A", 1L))
        .isInstanceOf(ValidationException.class)
        .extracting("field")
        .isEqualTo("userName");
  }

  @Test
  void emailMustContainAtAndDot() {
    assertThatThrownBy(() -> User.create("pablo", "invalid.example.com", "600000001", "admin", "12345678A", 1L))
        .isInstanceOf(ValidationException.class)
        .extracting("field")
        .isEqualTo("email");
    assertThatThrownBy(() -> User.create("pablo", "invalid@example", "600000001", "admin", "12345678A", 1L))
        .isInstanceOf(ValidationException.class)
        .extracting("field")
        .isEqualTo("email");
  }

  @Test
  void acceptsOnlyAdminAndSuperadminRoles() {
    assertThat(User.create("pablo", "a@b.c", "600000001", "admin", "12345678A", 1L).role())
        .isEqualTo(Role.ADMIN);
    assertThat(User.create("pablo", "a@b.c", "600000001", "superadmin", "12345678A", 1L).role())
        .isEqualTo(Role.SUPERADMIN);
    assertThatThrownBy(() -> User.create("pablo", "a@b.c", "600000001", "user", "12345678A", 1L))
        .isInstanceOf(ValidationException.class)
        .extracting("field")
        .isEqualTo("role");
  }
}
