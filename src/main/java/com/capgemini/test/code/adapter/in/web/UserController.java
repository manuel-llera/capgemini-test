package com.capgemini.test.code.adapter.in.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.capgemini.test.code.application.CreateUserUseCase;
import com.capgemini.test.code.application.GetUserUseCase;

@RestController
@RequestMapping("/users")
public class UserController {
  // La prueba fija la sala 1; sustituir esta constante por un roomId de ruta al permitir seleccionar sala.
  private static final long DEFAULT_ROOM = 1L;

  private final CreateUserUseCase createUserUseCase;
  private final GetUserUseCase getUserUseCase;
  private final UserApiMapper mapper;

  public UserController(
      CreateUserUseCase createUserUseCase,
      GetUserUseCase getUserUseCase,
      UserApiMapper mapper) {
    this.createUserUseCase = createUserUseCase;
    this.getUserUseCase = getUserUseCase;
    this.mapper = mapper;
  }

  @PostMapping
  public ResponseEntity<UserIdResponse> create(@RequestBody CreateUserRequest request) {
    Long id = createUserUseCase.create(mapper.toCommand(request), DEFAULT_ROOM);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(id)
            .toUri();
    return ResponseEntity.created(location).body(new UserIdResponse(id));
  }

  @GetMapping("/{userId}")
  public UserResponse get(@PathVariable Long userId) {
    return mapper.toResponse(getUserUseCase.get(userId, DEFAULT_ROOM));
  }
}
