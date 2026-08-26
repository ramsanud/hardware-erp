package com.hardware.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hardware Shop ERP API")
                        .version("v1")
                        .description("""
                                Single-shop Hardware ERP. Module 1: Authentication
                                and User Management.

                                **How to test a protected endpoint**

                                1. `POST /api/v1/auth/login` with `identifier` and
                                   `password`.
                                2. Copy `data.accessToken` from the response.
                                3. Click **Authorize** at the top right and paste the
                                   token (no "Bearer " prefix - Swagger adds it).
                                4. Call any endpoint marked with a padlock.

                                Access tokens last 15 minutes. When one expires, call
                                `POST /api/v1/auth/refresh` and authorize again with
                                the new token.

                                There is no self-registration endpoint. Accounts are
                                created by the owner at `POST /api/v1/users`.""")
                        .contact(new Contact().name("Hardware ERP"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080/api").description("Local"),
                        new Server().url("/api").description("Same origin")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken value only.")));
    }

    /** One group per module keeps the UI navigable as Modules 2-12 land. */
    @Bean
    public GroupedOpenApi authenticationApi() {
        return GroupedOpenApi.builder()
                .group("01-authentication")
                .displayName("Module 1 - Authentication & Users")
                .pathsToMatch("/v1/auth/**", "/v1/users/**", "/v1/roles/**",
                        "/v1/permissions/**", "/v1/security-audit-logs/**")
                .build();
    }

    @Bean
    public GroupedOpenApi supplierApi() {
        return GroupedOpenApi.builder()
                .group("02-supplier")
                .displayName("Module 2 - Suppliers")
                .pathsToMatch("/v1/suppliers/**")
                .build();
    }

    @Bean
    public GroupedOpenApi productApi() {
        return GroupedOpenApi.builder()
                .group("03-product")
                .displayName("Module 3 - Categories, Brands & Products")
                .pathsToMatch("/v1/categories/**", "/v1/brands/**", "/v1/products/**")
                .build();
    }

    @Bean
    public GroupedOpenApi inventoryApi() {
        return GroupedOpenApi.builder()
                .group("04-inventory")
                .displayName("Module 4 - Inventory")
                .pathsToMatch("/v1/stock/**")
                .build();
    }

    @Bean
    public GroupedOpenApi invoiceApi() {
        return GroupedOpenApi.builder()
                .group("07-invoice-payment")
                .displayName("Module 7 - Invoice & Payment")
                .pathsToMatch("/v1/invoices/**")
                .build();
    }
}
