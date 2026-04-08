package com.ems.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class GatewayConfig {
  @Bean
  public RouteLocator customRoutesLocator(RouteLocatorBuilder builder) {
    return builder.routes()
            .route("employee-service", r -> r
                    .path("/employee-service/**")
                    .filters(f -> f.stripPrefix(1))
                    .uri("lb://employee-service")
            )
            .route("auth-service", r -> r
                    .path("/auth-service/**")
                    .filters(f -> f.stripPrefix(1))
                    .uri("lb://auth-service")
            )
            .build();
  }


  @Bean
  public CorsWebFilter corsWebFilter() {
    CorsConfiguration corsConfig = new CorsConfiguration();
    corsConfig.setAllowCredentials(true);
    corsConfig.setAllowedOrigins(List.of(
            "http://localhost:4200",
            "https://ems-client-eight.vercel.app"
//            "http://192.168.1.156:4200/",
//            "http://192.168.166.4:4200/",
//            "https://faaab287c786.ngrok-free.app"
    ));
    corsConfig.addAllowedHeader("*");
    corsConfig.addAllowedMethod("*");

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", corsConfig);

    return new CorsWebFilter(source);
  }
}
