package com.ems.apigateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;


@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
  @Value("${application.security.jwt.secret-key}")
  private String jwtSecret;

  private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private final List<String> excludedPaths = Arrays.asList(
          "/auth-service/api/auth/login",
//          "/employee-service/api/v1/employee/email",
          "/actuator"
  );

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    if (excludedPaths.stream().anyMatch(path::startsWith)) {
      return chain.filter(exchange);
    }

    return extractToken(exchange)
            .flatMap(this::validateToken)
            .flatMap(claims -> {
              if (claims.getExpiration().before(new Date())) {
                return Mono.error(new RuntimeException("Expired JWT token"));
              }

              Object rolesObj = claims.get("role");
              String roles = "";
              if (rolesObj instanceof Set<?>) {
                @SuppressWarnings("unchecked")
                Set<String> roleList = (Set<String>) rolesObj;
                roles = String.join(",", roleList);
              } else if (rolesObj instanceof String) {
                roles = (String) rolesObj;
              }

              // Add user info to request headers
              ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                      .header(UserHttpHeaders.X_EMPLOYEE_ID, claims.getSubject())
                      .header(UserHttpHeaders.X_EMAIL, String.valueOf(claims.get("email")))
                      .header(UserHttpHeaders.X_EMPLOYEE_ROLE, roles) //String.valueOf(claims.get("roles")))
                      .build();

              return chain.filter(exchange.mutate().request(mutatedRequest).build());
            })
            .onErrorResume(throwable -> {
              logger.error("JWT validation failed: {}", throwable.getMessage());
              return handleUnauthorized(exchange);
            });
  }

  private Mono<String> extractToken(ServerWebExchange exchange) {
    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return Mono.just(authHeader.substring(7));
    }

    return Mono.error(new RuntimeException("No valid token found"));
  }

  private Mono<Claims> validateToken(String token) {
    try {
      Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

      Claims claims = Jwts.parserBuilder()
              .setSigningKey(key)
              .build()
              .parseClaimsJws(token)
              .getBody();

      return Mono.just(claims);
    } catch (Exception e) {
      return Mono.error(new RuntimeException("Invalid token: " + e.getMessage()));
    }
  }

  private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().add("Content-Type", "application/json");

    String body = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing token\"}";
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());

    return response.writeWith(Mono.just(buffer));
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
