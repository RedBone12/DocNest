package com.priyanshu.api_gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;

@Configuration
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${docnest.cors-origins:http://localhost:[*],http://127.0.0.1:[*]}") String origins) {
        logger.info("Configuring CORS filter");
        
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(Arrays.asList(origins.split(",")));
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        corsConfig.setExposedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Requested-With"));
        corsConfig.setAllowCredentials(false);
        corsConfig.setMaxAge(3600L);
        
        logger.debug("CORS configuration: configured origin patterns, allowed methods={}, allow credentials={}",
                    corsConfig.getAllowedMethods(), corsConfig.getAllowCredentials());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return source;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
            @Value("${docnest.auth-service-url:http://localhost:8081}") String authServiceUrl) {
        logger.info("Configuring security filter chain");
        
        try {
            return http
                .csrf(csrf -> {
                    csrf.disable();
                    logger.debug("CSRF protection disabled");
                })
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .addFilterBefore(new SessionValidationFilter(authServiceUrl), SecurityWebFiltersOrder.AUTHORIZATION)
                .exceptionHandling(exceptionHandling -> {
                    exceptionHandling.accessDeniedHandler((exchange, ex) -> {
                        logger.warn("Access denied for request to {}: {}", 
                                   exchange.getRequest().getPath(), ex.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    });
                    exceptionHandling.authenticationEntryPoint((exchange, ex) -> {
                        logger.warn("Authentication failed for request to {}: {}", 
                                   exchange.getRequest().getPath(), ex.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    });
                })
                .authorizeExchange(ex -> {
                    ex.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    ex.pathMatchers("/auth/**", "/actuator/health").permitAll();
                    ex.anyExchange().authenticated();
                    logger.debug("Authorization rules configured - public paths: /auth/**, /actuator/health, OPTIONS requests");
                })
                .oauth2ResourceServer(oauth -> {
                    oauth.jwt();
                    logger.debug("OAuth2 resource server configured with JWT");
                })
                .build();
        } catch (Exception e) {
            logger.error("Error configuring security filter chain", e);
            throw new RuntimeException("Security configuration failed", e);
        }
    }
}
