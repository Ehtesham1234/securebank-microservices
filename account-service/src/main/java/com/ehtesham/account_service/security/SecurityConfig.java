package com.ehtesham.account_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final GatewayAuthFilter gatewayAuthFilter;

    public SecurityConfig(GatewayAuthFilter gatewayAuthFilter) {
        this.gatewayAuthFilter = gatewayAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                // Swagger — allow in dev
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        // C1-systemic fix: coarse defense-in-depth net —
                        // if a future admin endpoint here is ever added
                        // without @PreAuthorize, this still blocks it
                        // instead of silently allowing any authenticated
                        // customer through, which is exactly what
                        // happened to AccountController before this fix.
                        .requestMatchers("/api/v1/admin/**")
                        .hasAuthority("ROLE_ADMIN")
                        // "/api/v1/internal/**" used to be permitAll here
                        // (stale — predates the JWT-forwarding refactor,
                        // back when it was protected by network topology
                        // instead). It now requires authentication like
                        // everything else; @PreAuthorize on
                        // InternalAccountController's methods enforces
                        // WHO may call each one.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        gatewayAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}