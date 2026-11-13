package com.opc.web.core.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.opc.framework.config.SecurityConfig;
import com.opc.framework.config.properties.PermitAllUrlProperties;
import com.opc.framework.security.filter.JwtAuthenticationTokenFilter;
import com.opc.framework.security.handle.AuthenticationEntryPointImpl;
import com.opc.framework.security.handle.LogoutSuccessHandlerImpl;
import com.opc.common.core.redis.RedisCache;
import com.opc.framework.web.service.TokenService;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;

@SpringBootTest(
        classes = HealthEndpointSecurityTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=health",
                "management.endpoint.health.show-details=never",
                "management.health.db.enabled=false",
                "management.health.redis.enabled=false",
                "token.secret=health-endpoint-security-test-secret"
        })
class HealthEndpointSecurityTest
{
    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void exposesStandardHealthResponseWithoutAuthentication() throws Exception
    {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/vnd.spring-boot.actuator.v3+json"));
        assertTrue(response.body().contains("\"status\":\"UP\""));
        assertFalse(response.body().contains("\"code\""));
    }

    @Test
    void doesNotExposeEnvironmentEndpoint() throws Exception
    {
        HttpResponse<String> response = get("/actuator/env");

        assertTrue(response.statusCode() == 404 || response.statusCode() == 401);
        assertFalse(response.statusCode() == 200);
    }

    @Test
    void keepsBusinessEndpointsProtectedByHttpUnauthorizedStatus() throws Exception
    {
        HttpResponse<String> response = get("/private-test");

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"code\":401"));
    }

    private HttpResponse<String> get(String path) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class })
    @Import(SecurityConfig.class)
    static class TestApplication
    {
        @Bean
        AuthenticationEntryPointImpl authenticationEntryPoint()
        {
            return new AuthenticationEntryPointImpl();
        }

        @Bean
        LogoutSuccessHandlerImpl logoutSuccessHandler()
        {
            return mock(LogoutSuccessHandlerImpl.class);
        }

        @Bean
        TokenService tokenService()
        {
            return new TokenService();
        }

        @Bean
        RedisCache redisCache()
        {
            return new RedisCache();
        }

        @Bean
        JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter()
        {
            return new JwtAuthenticationTokenFilter();
        }

        @Bean
        CorsFilter corsFilter()
        {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of("http://localhost"));
            configuration.setAllowedMethods(List.of("GET"));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return new CorsFilter(source);
        }

        @Bean
        PermitAllUrlProperties permitAllUrlProperties()
        {
            return new PermitAllUrlProperties()
            {
                @Override
                public void afterPropertiesSet()
                {
                    setUrls(List.of());
                }
            };
        }

        @Bean
        PrivateTestController privateTestController()
        {
            return new PrivateTestController();
        }
    }

    @RestController
    static class PrivateTestController
    {
        @GetMapping("/private-test")
        String privateEndpoint()
        {
            return "private";
        }
    }
}
