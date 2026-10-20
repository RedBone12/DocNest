package com.priyanshu.api_gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AccountGatewayTest {
    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    @Import(SecurityConfig.class)
    static class Config {
        @Bean ReactiveJwtDecoder jwtDecoder() {
            return new JwtDecoderConfig("test-only-gateway-signing-key-more-than-32-bytes").jwtDecoder();
        }
        @Bean RouterFunction<ServerResponse> routes() {
            return RouterFunctions.route().GET("/auth/capabilities", request -> ServerResponse.ok().bodyValue("ok"))
                    .GET("/documents", request -> ServerResponse.ok().bodyValue("documents")).build();
        }
    }

    @Test void realSecurityChainChecksRevocationBeforeAuthorizingUserRequests() throws Exception {
        AtomicInteger status = new AtomicInteger(401);
        AtomicInteger checked = new AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/session", exchange -> {
            checked.incrementAndGet();
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",
                    java.util.Map.of("docnest.auth-service-url", "http://127.0.0.1:" + server.getAddress().getPort())));
            context.register(Config.class); context.refresh();
            var client = WebTestClient.bindToApplicationContext(context).configureClient().baseUrl("http://localhost:8080").build();
            String token = signedUserToken();
            client.get().uri("/documents").header("Authorization", "Bearer " + token).header("Origin", "http://localhost:5173")
                    .exchange().expectStatus().isUnauthorized().expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
            status.set(200);
            client.get().uri("/documents").header("Authorization", "Bearer " + token).exchange().expectStatus().isOk();
            status.set(503);
            client.get().uri("/documents").header("Authorization", "Bearer " + token).exchange().expectStatus().isEqualTo(503);
            assertThat(checked.get()).isEqualTo(3);
        } finally { server.stop(0); }
    }

    private static String signedUserToken() throws Exception {
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"user-id\",\"type\":\"user\",\"exp\":"
                + Instant.now().plusSeconds(600).getEpochSecond() + "}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = header + "." + payload;
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-only-gateway-signing-key-more-than-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return body + "." + encoder.encodeToString(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test void authenticationErrorsKeepCorsHeadersAndUnknownOriginsAreRejected() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var client = WebTestClient.bindToApplicationContext(context).configureClient().baseUrl("http://localhost:8080").build();
            client.get().uri("/documents").header("Origin", "http://localhost:5173").exchange()
                    .expectStatus().isUnauthorized().expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
            client.get().uri("/documents").header("Origin", "http://localhost:5173")
                    .header("Authorization", "Bearer invalid-token").exchange().expectStatus().isUnauthorized()
                    .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
            client.options().uri("/documents").header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization")
                    .exchange().expectStatus().isOk().expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
            client.get().uri("/auth/capabilities").header("Origin", "https://untrusted.example").exchange().expectStatus().isForbidden();
        }
    }

    @Test void validSessionRoutesButRevokedSessionAndOutageDoNot() {
        for (int status : new int[] {200, 401, 403, 500, 503}) {
            AtomicInteger routed = new AtomicInteger();
            WebClient client = WebClient.builder().exchangeFunction(request -> {
                assertThat(request.url().getPath()).isEqualTo("/auth/session");
                assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer test-user-token");
                return Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).build());
            }).build();
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/search/documents"));
            var jwt = Jwt.withTokenValue("test-user-token").header("alg", "HS256").subject("user-id")
                    .claim("type", "user").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
            var authenticated = exchange.mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
            new SessionValidationFilter(client).filter(authenticated, ignored -> {
                routed.incrementAndGet(); return Mono.empty();
            }).block(Duration.ofSeconds(2));
            assertThat(routed.get()).isEqualTo(status == 200 ? 1 : 0);
            if (status != 200) {
                assertThat(exchange.getResponse().getStatusCode().value())
                        .isEqualTo(status == 401 || status == 403 ? 401 : 503);
            }
        }
    }

    @Test void transportFailureFailsClosedWithoutMisreportingAnExpiredSession() {
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.error(new IllegalStateException("offline"))).build();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/documents"));
        var jwt = Jwt.withTokenValue("test-user-token").header("alg", "HS256").subject("user-id").claim("type", "user").build();
        var authenticated = exchange.mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
        new SessionValidationFilter(client).filter(authenticated, ignored -> { throw new AssertionError("must not route"); }).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void serviceTokensBypassUserSessionLookup() {
        assertTokenTypeRouting("service", true);
    }

    @Test void missingAndUnknownTokenTypesAreRejected() {
        assertTokenTypeRouting(null, false);
        assertTokenTypeRouting("unexpected", false);
    }

    private void assertTokenTypeRouting(String type, boolean shouldRoute) {
        AtomicInteger routed = new AtomicInteger();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            throw new AssertionError("These token types must not query user sessions");
        }).build();
        var builder = Jwt.withTokenValue("token").header("alg", "HS256").subject("subject");
        if (type != null) { builder.claim("type", type); }
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/documents"));
        var authenticated = exchange.mutate()
                .principal(Mono.just(new JwtAuthenticationToken(builder.build()))).build();
        new SessionValidationFilter(client).filter(authenticated, ignored -> {
            routed.incrementAndGet();
            return Mono.empty();
        }).block(Duration.ofSeconds(2));
        assertThat(routed.get()).isEqualTo(shouldRoute ? 1 : 0);
        if (!shouldRoute) {
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test void absentAndNonJwtPrincipalsContinueWithoutSessionLookup() {
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            throw new AssertionError("Non-JWT requests must not query user sessions");
        }).build();
        var filter = new SessionValidationFilter(client);
        AtomicInteger routed = new AtomicInteger();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/capabilities"));
        filter.filter(exchange, ignored -> { routed.incrementAndGet(); return Mono.empty(); })
                .block(Duration.ofSeconds(2));
        java.security.Principal principal = () -> "test-principal";
        filter.filter(exchange.mutate().principal(Mono.just(principal)).build(), ignored -> {
            routed.incrementAndGet(); return Mono.empty();
        }).block(Duration.ofSeconds(2));
        assertThat(routed.get()).isEqualTo(2);
    }
}
