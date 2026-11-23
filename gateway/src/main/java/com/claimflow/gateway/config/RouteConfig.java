package com.claimflow.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RouteConfig {

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties properties) {
        GatewayProperties.Services services = properties.services();
        return builder.routes()
                .route("member-service", r -> r.path("/api/members/**", "/api/plans/**")
                        .uri(services.member()))
                .route("claims-service", r -> r.path("/api/claims/**")
                        .uri(services.claims()))
                .route("audit-service", r -> r.path("/api/audit/**")
                        .uri(services.audit()))
                .build();
    }
}
