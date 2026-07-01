package com.example.taskmanager.domain.entity;

import com.example.taskmanager.domain.enums.UserRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ユーザー JPA エンティティ。
 *
 * Java Gold トピック:
 * - @PrePersist によるライフサイクルコールバック
 * - @Enumerated(EnumType.STRING) による Enum マッピング
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
        // JPA用デフォルトコンストラクタ
    }

    public User(String username, String password, String displayName, UserRole role) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
