package com.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wallet Service API")
                        .version("1.0.0")
                        .description("""
                                Mission-critical wallet service for managing user funds.
                                Supports deposit, withdrawal, transfer, and full transaction history.
                                All monetary operations are fully traceable for audit purposes.
                                """)
                        .contact(new Contact().name("Wallet Team")));
    }
}
