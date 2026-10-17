package com.priyanshu.api_gateway.security;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Checks server-side revocation before routing user requests, including search. */
public class SessionValidationFilter implements WebFilter {
    private final WebClient client;
    public SessionValidationFilter(String authServiceUrl) {
        this(WebClient.builder().baseUrl(authServiceUrl).build());
    }
    SessionValidationFilter(WebClient client) { this.client = client; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal().map(principal -> {
            if (principal instanceof JwtAuthenticationToken authentication) {
                String type = authentication.getToken().getClaimAsString("type");
                if ("service".equals(type)) return chain.filter(exchange);
                if (!"user".equals(type)) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
                return client.get().uri("/auth/session")
                        .headers(headers -> headers.setBearerAuth(authentication.getToken().getTokenValue()))
                        .exchangeToMono(response -> Mono.just(response.statusCode().value()))
                        .timeout(Duration.ofSeconds(5))
                        .onErrorReturn(503)
                        .flatMap(status -> {
                            if (status == 200) return chain.filter(exchange);
                            exchange.getResponse().setStatusCode(status == 401 || status == 403
                                    ? HttpStatus.UNAUTHORIZED : HttpStatus.SERVICE_UNAVAILABLE);
                            return exchange.getResponse().setComplete();
                        });
            }
            return chain.filter(exchange);
        }).switchIfEmpty(Mono.fromSupplier(() -> chain.filter(exchange))).flatMap(result -> result);
    }
}
