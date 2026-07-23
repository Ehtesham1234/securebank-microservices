package com.ehtesham.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(
				ApiGatewayApplication.class, args);
	}

	@Bean
	public CorsWebFilter corsWebFilter() {

		CorsConfiguration config = new CorsConfiguration();

		// Allowed frontend origins
		config.setAllowedOrigins(List.of(
				"http://localhost:3000",
				"http://localhost:5173",
				"http://localhost:5500",
				"http://127.0.0.1:5500"
		));

		config.setAllowedMethods(List.of(
				"GET", "POST", "PUT",
				"PATCH", "DELETE", "OPTIONS"
		));

		config.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"Idempotency-Key",
				"X-User-Id",
				"X-User-Role",
				"X-User-Email",
				"X-User-Status"
		));

		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source =
				new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		return new CorsWebFilter(source);
	}
}