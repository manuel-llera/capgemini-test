package com.capgemini.test.code.application;

public record CreateUserCommand(
    String name, String email, String phone, String role, String dni) {}
