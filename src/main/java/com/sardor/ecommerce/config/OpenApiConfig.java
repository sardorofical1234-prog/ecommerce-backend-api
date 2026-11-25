package com.sardor.ecommerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI ecommerceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("E-commerce Backend API")
                .version("v1")
                .description("REST API for customer accounts and product catalog. Cart, checkout and order modules are planned."));
    }
}
