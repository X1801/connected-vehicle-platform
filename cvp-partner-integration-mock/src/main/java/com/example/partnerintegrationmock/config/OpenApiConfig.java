package com.example.partnerintegrationmock.config;
import io.swagger.v3.oas.models.OpenAPI; import io.swagger.v3.oas.models.info.Info; import org.springframework.context.annotation.*;
@Configuration public class OpenApiConfig { @Bean OpenAPI api(){return new OpenAPI().info(new Info().title("Partner Integration Mock API").version("v1").description("Development-only OEM Partner Integration simulation."));} }
