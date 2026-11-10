package com.cvp.maintenance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        http.csrf(csrf -> csrf.disable());
        // A real configured issuer enables local JWT signature and claim validation. Local Compose deliberately
        // omits it because its deterministic HTTP mocks are not an identity provider.
        String issuer = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        if (issuer != null && !issuer.isBlank()) {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .anyRequest().authenticated());
            http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> { }));
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        }
        return http.build();
    }
}
