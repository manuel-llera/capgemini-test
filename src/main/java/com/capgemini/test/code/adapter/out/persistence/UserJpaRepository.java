package com.capgemini.test.code.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
  boolean existsByEmail(String email);

  Optional<UserEntity> findByIdAndRoom_Id(Long id, Long roomId);
}
