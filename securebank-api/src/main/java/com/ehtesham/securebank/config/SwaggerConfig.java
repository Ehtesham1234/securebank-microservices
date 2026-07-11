package com.ehtesham.securebank.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI secureBankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SecureBank API")
                        .description(
                                "A production-grade banking REST API " +
                                        "with JWT authentication, KYC verification, " +
                                        "loan management, card services, " +
                                        "and real-time transaction processing.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Ehtesham")
                                .url("https://github.com/Ehtesham1234"))
                        .license(new License()
                                .name("MIT License")))
                // tells Swagger: "this API uses Bearer JWT tokens"
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes(
                                "Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Enter your JWT access token. " +
                                                        "Obtain it via POST /api/v1/auth/login")));
    }
}