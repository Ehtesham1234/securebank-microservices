package com.ehtesham.loan_service.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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
                        .title("SecureBank — Loan Service API")
                        .description("""
                                Lending service managing loan lifecycle
                                with Saga choreography for safe disbursement.

                                Handles:
                                - Loan applications (PERSONAL/HOME/CAR)
                                - Teller/Admin approval and rejection
                                - EMI payment tracking with reducing balance
                                - Loan disbursement Saga with Circuit Breaker
                                - Automatic overdue detection and defaulting
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SecureBank Team")
                                .email("support@securebank.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8084")
                                .description("Local development"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Via API Gateway")));
    }
}