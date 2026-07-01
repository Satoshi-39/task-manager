package com.example.taskmanager.repository;

import com.example.taskmanager.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ユーザー JPA リポジトリ。
 *
 * Java Gold トピック:
 * - Optional 戻り値による null 安全な検索
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
