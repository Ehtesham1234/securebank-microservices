package com.ehtesham.securebank.security.config;

import com.ehtesham.securebank.security.filter.UserStatusFilter;
import com.ehtesham.securebank.security.filter.JwtAuthenticationFilter;
import com.ehtesham.securebank.security.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@EnableMethodSecurity
@Configuration
public class SecurityConfig {
    private final CustomUserDetailsService userDetailsService;
    private final  JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserStatusFilter userStatusFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter1, UserStatusFilter userStatusFilter, CorsConfigurationSource corsConfigurationSource) {

        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter1;
        this.userStatusFilter = userStatusFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/kyc/**")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_TELLER", "ROLE_ADMIN")
                        .requestMatchers("/api/v1/teller/**")
                        .hasAnyAuthority("ROLE_TELLER", "ROLE_ADMIN")
                        .requestMatchers("/api/v1/admin/**")
                        .hasAuthority("ROLE_ADMIN")
                        // Add to your .requestMatchers().permitAll() chain:
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/v3/api-docs/**",        // raw OpenAPI JSON/YAML
                                "/swagger-ui/**",          // Swagger UI static files
                                "/swagger-ui.html",        // Swagger UI entry point
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(
                        userStatusFilter,
                        JwtAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }
}