package org.tw.token_billing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Token Usage Billing API")
                .version("1.0")
                .description("Submits LLM token usage and returns an authoritative bill that " +
                    "consumes the customer's monthly quota first, then charges overage."))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(new OAuthFlows()
                        .clientCredentials(new OAuthFlow()
                            .tokenUrl("${JWT_ISSUER_URI}/protocol/openid-connect/token")
                            .scopes(new Scopes().addString("billing:write", "Submit token usage and create bills"))
                        )
                    )
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth", "billing:write"));
    }
}
