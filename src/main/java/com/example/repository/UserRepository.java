package com.example.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entity.User;

/**
 * {@link User} 리포지토리.
 *
 * <p>{@code User}에 적용된 {@code @SQLRestriction("deleted_at IS NULL")}이 아래 파생 쿼리를 포함한 모든 쿼리에 자동으로 조건을
 * 덧붙이므로, 메서드명에 {@code AndDeletedAtIsNull}을 별도로 붙이지 않는다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
