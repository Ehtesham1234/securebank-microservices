package com.ehtesham.securebank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // which frontend origins are allowed to call this API
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",   // React dev server (default)
                "http://localhost:5173"    // Vite dev server (default)
        ));

        // which HTTP methods are allowed cross-origin
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // which request headers the frontend is allowed to send
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type"
        ));

        // allow the browser to send credentials (needed if you
        // ever use cookies; harmless to leave true for JWT-in-header too)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // apply this CORS policy to EVERY endpoint
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}