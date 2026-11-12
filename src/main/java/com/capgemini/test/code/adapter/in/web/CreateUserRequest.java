package com.capgemini.test.code.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateUserRequest(
    String name,
    String email,
    String phone,
    @JsonProperty("rol") String role,
    String dni) {}
