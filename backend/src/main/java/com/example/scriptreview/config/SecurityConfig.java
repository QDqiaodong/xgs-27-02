package com.example.scriptreview.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                // 原稿与代号映射是管理员受控视图，HTTP 层与服务层双重强制 ADMIN
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/api/scripts/*/raw", "/api/scripts/*/identity-map").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/scripts/*/identity-groups/*/decision").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/scripts").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/scripts/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/scripts/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/scripts/**")
                    .hasAnyRole("ADMIN", "JUDGE", "VIEWER")
                .requestMatchers("/api/judges/**").hasAnyRole("ADMIN", "JUDGE")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/scores/script/**")
                    .hasAnyRole("ADMIN", "JUDGE", "VIEWER")
                .requestMatchers("/api/scores/**").hasAnyRole("ADMIN", "JUDGE")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/assignments").hasRole("ADMIN")
                .requestMatchers("/api/assignments/**").hasAnyRole("ADMIN", "JUDGE")
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/desensitization/**").hasRole("ADMIN")
                .requestMatchers("/api/statistics/**").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers("/api/logs/**").hasRole("ADMIN")
                .requestMatchers("/api/recusals/**").hasAnyRole("ADMIN", "JUDGE", "VIEWER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}