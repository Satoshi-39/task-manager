package com.example.taskmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
/**
 * Spring Security の設定。
 *
 * Java Gold トピック:
 * - SecurityFilterChain による宣言的セキュリティ設定
 * - BCrypt によるパスワードハッシュ化
 * - URL ベースの認可設定
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 静的リソース・ログインページは許可
                .requestMatchers("/css/**", "/js/**", "/login", "/error").permitAll()
                // H2 Console は許可
                .requestMatchers("/h2-console/**").permitAll()
                // バッチ・エクスポート系は ADMIN のみ
                .requestMatchers("/api/batch/**").hasRole("ADMIN")
                .requestMatchers("/api/tasks/batch/**").hasRole("ADMIN")
                .requestMatchers("/api/tasks/export/**").hasRole("ADMIN")
                // その他は認証必須
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // REST API は Basic 認証も許可（curl 等からのアクセス用）
            .httpBasic(basic -> {})
            .csrf(csrf -> csrf
                // REST API は CSRF 無効
                .ignoringRequestMatchers("/api/**")
                // H2 Console は CSRF 無効
                .ignoringRequestMatchers("/h2-console/**")
            )
            // H2 Console のフレーム表示を許可
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
