package com.ehtesham.api_gateway;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class ApiGatewayApplication {

	private static final Logger log =
			LoggerFactory.getLogger(ApiGatewayApplication.class);

	// CORS fix: this is the ONLY place CORS should be configured in the
	// whole system. The gateway is the sole thing a browser ever talks
	// to — no downstream service's port is published, and this filter
	// is registered for "/**", covering every route to every service.
	// securebank-api used to have its own separate CorsConfig too;
	// removed, since it was silently duplicating this one and the two
	// together produced a response with Access-Control-Allow-Origin
	// listed twice, which browsers correctly reject outright.
	@Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:5173,http://localhost:5500,http://127.0.0.1:5500}")
	private String allowedOriginsProperty;

	@PostConstruct
	public void validateCorsConfig() {
		if (allowedOriginsProperty.contains("*")) {
			// Actively dangerous combined with setAllowCredentials(true)
			// below — a wildcard origin plus credentials lets ANY site
			// make authenticated requests on a logged-in user's behalf.
			throw new IllegalStateException(
					"cors.allowed-origins must not contain '*' — this "
							+ "gateway allows credentialed requests, so a "
							+ "wildcard origin would let any website act "
							+ "on behalf of a logged-in user.");
		}
		log.info("CORS allowed origins: {}", allowedOriginsProperty);
	}

	public static void main(String[] args) {
		SpringApplication.run(
				ApiGatewayApplication.class, args);
	}

	@Bean
	public CorsWebFilter corsWebFilter() {

		CorsConfiguration config = new CorsConfiguration();

		List<String> allowedOrigins = Arrays.stream(
						allowedOriginsProperty.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isBlank())
				.toList();

		config.setAllowedOrigins(allowedOrigins);

		config.setAllowedMethods(List.of(
				"GET", "POST", "PUT",
				"PATCH", "DELETE", "OPTIONS"
		));

		// X-User-* removed — those are internal, gateway-to-service
		// headers only (see GatewayAuthFilter), derived from the
		// verified JWT. The frontend never sends or reads them; it only
		// ever needs to send Authorization (its JWT) and Idempotency-Key.
		config.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"Idempotency-Key",
				"X-Account-Id"
		));

		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source =
				new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		return new CorsWebFilter(source);
	}
}