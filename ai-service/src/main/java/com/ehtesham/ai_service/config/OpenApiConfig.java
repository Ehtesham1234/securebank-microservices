package com.ehtesham.ai_service.config;

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
                        .title("SecureBank — AI Assistant API")
                        .description("""
                                AI-powered banking assistant using Spring AI 2.0.0.

                                Handles:
                                - Natural language queries about accounts and balances
                                - Loan status and EMI information
                                - Spending analysis and transaction history
                                - Financial summaries with structured output
                                - Streaming responses via Server-Sent Events
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SecureBank Team")
                                .email("support@securebank.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8085")
                                .description("Local development"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Via API Gateway")));
    }
}