package com.p2pwallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers global OpenAPI / Swagger metadata for the Job Scheduler API.
 * The Swagger UI is available at /swagger-ui.html and the raw spec at /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI p2pWalletOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("P2PWallet Service API")
                        .description(
                                "REST API for P2P wallet transfers")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("P2P Wallet Service Team")
                                .email("p2pwallet@example.com")));
    }
}
