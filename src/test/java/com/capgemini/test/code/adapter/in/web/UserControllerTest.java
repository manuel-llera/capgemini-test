package com.capgemini.test.code.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.capgemini.test.code.application.CreateUserCommand;
import com.capgemini.test.code.application.CreateUserUseCase;
import com.capgemini.test.code.application.GetUserUseCase;
import com.capgemini.test.code.domain.Role;
import com.capgemini.test.code.domain.User;
import com.capgemini.test.code.domain.exception.ValidationException;

@WebMvcTest(UserController.class)
@Import(UserApiMapper.class)
class UserControllerTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private CreateUserUseCase createUserUseCase;
  @MockBean private GetUserUseCase getUserUseCase;

  @Test
  void postReturns201IdAndLocation() throws Exception {
    when(createUserUseCase.create(any(CreateUserCommand.class), eq(1L))).thenReturn(42L);

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"pablo","email":"pablo@example.com","phone":"600000001","rol":"admin","dni":"12345678A"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/users/42")));

    verify(createUserUseCase)
        .create(
            new CreateUserCommand("pablo", "pablo@example.com", "600000001", "admin", "12345678A"),
            1L);
  }

  @Test
  void getReturnsUserInRoomOne() throws Exception {
    when(getUserUseCase.get(42L, 1L))
        .thenReturn(new User(42L, "pablo", "pablo@example.com", "600000001",
            Role.ADMIN, "12345678A", 1L));

    mockMvc
        .perform(get("/users/42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.name").value("pablo"))
        .andExpect(jsonPath("$.rol").value("admin"));

    verify(getUserUseCase).get(42L, 1L);
  }

  @Test
  void validationFailureUsesTheDocumentedConflictBody() throws Exception {
    when(createUserUseCase.create(any(CreateUserCommand.class), eq(1L)))
        .thenThrow(new ValidationException("userName"));

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"toolong","email":"pablo@example.com","phone":"600000001","rol":"admin","dni":"12345678A"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(409))
        .andExpect(jsonPath("$.message").value("error validation userName"));
  }

  @Test
  void missingUserReturns404() throws Exception {
    when(getUserUseCase.get(42L, 1L))
        .thenThrow(new com.capgemini.test.code.domain.exception.UserNotFoundException(42L));

    mockMvc
        .perform(get("/users/42"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("User not found: 42"));
  }
}
