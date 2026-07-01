package com.example.taskmanager.security;

import com.example.taskmanager.domain.entity.User;
import com.example.taskmanager.domain.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security の UserDetails 実装。
 * ユーザー ID やロール情報を SecurityContext 経由で取得可能にする。
 *
 * Java Gold トピック:
 * - インタフェース実装（UserDetails）
 * - List.of() による不変リスト生成
 */
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String displayName;
    private final UserRole role;
    private final boolean enabled;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }
}
