package com.priyanshu.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.annotation.DirtiesContext;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        properties = {
                "security.jwt.secret=api-gateway-test-secret-at-least-32-bytes-long",
                "logging.file.name=target/test-logs/api-gateway.log"
        })
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiGatewayApplicationTests {
    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void mainStartsGatewayWithConfiguredRoutes() {
        assertTrue(context.isActive());
        assertNotNull(context.getBean(ApiGatewayApplication.class));
        var ids = routeLocator.getRoutes().map(route -> route.getId())
                .collectList().block(java.time.Duration.ofSeconds(5));
        assertNotNull(ids);
        assertTrue(ids.containsAll(java.util.List.of(
                "document-service-docs", "document-service-auth", "search-service")));
    }
}
