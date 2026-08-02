package com.ehtesham.securebank.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private static final Logger log =
            LoggerFactory.getLogger(CorsConfig.class);

    // M4 fix: externalized so the real deployed frontend origin is
    // configured per-environment instead of hand-edited into this file —
    // set CORS_ALLOWED_ORIGINS (comma-separated) in application-prod.properties
    // / the environment. Defaults to the previous localhost dev URLs so
    // local development is unaffected.
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:5173,http://localhost:5500,http://127.0.0.1:5500}")
    private String allowedOriginsProperty;

    @PostConstruct
    public void validate() {
        if (allowedOriginsProperty.contains("*")) {
            // Actively dangerous combined with setAllowCredentials(true)
            // below — a wildcard origin plus credentials lets ANY site
            // make authenticated requests on a logged-in user's behalf.
            throw new IllegalStateException(
                    "cors.allowed-origins must not contain '*' — this " +
                            "service allows credentialed requests, so a " +
                            "wildcard origin would let any website act on " +
                            "behalf of a logged-in user.");
        }
        log.info("CORS allowed origins: {}", allowedOriginsProperty);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        List<String> allowedOrigins = Arrays.stream(
                        allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        // which frontend origins are allowed to call this API
        configuration.setAllowedOrigins(allowedOrigins);

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