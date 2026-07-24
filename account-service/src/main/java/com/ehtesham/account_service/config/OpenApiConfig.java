package com.ehtesham.account_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SecureBank — Account Service API")
                        .description("""
                                Core banking service managing accounts,
                                transactions, and cards.

                                Handles:
                                - Account management (SAVINGS/CURRENT/FIXED_DEPOSIT)
                                - Deposits, withdrawals, transfers with idempotency
                                - Debit and credit card operations
                                - Credit card statements and bill payments
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SecureBank Team")
                                .email("support@securebank.com"))
                        .license(new License()
                                .name("Private")
                                .url("https://securebank.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8082")
                                .description("Local development"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Via API Gateway")));
    }
}