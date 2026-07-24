package com.ehtesham.kyc_service.config;

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
                        .title("SecureBank — KYC Service API")
                        .description("""
                                KYC compliance service managing document
                                verification and customer onboarding.

                                Handles:
                                - KYC document submission with file upload
                                - Teller verification and rejection workflow
                                - Customer activation after KYC approval
                                - Automatic savings account + debit card creation
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SecureBank Team")
                                .email("support@securebank.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8086")
                                .description("Local development"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Via API Gateway")));
    }
}