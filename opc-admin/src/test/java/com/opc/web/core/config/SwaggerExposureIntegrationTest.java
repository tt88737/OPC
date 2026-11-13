package com.opc.web.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.opc.common.core.redis.RedisCache;
import com.opc.framework.config.ResourcesConfig;
import com.opc.framework.config.SecurityConfig;
import com.opc.framework.config.properties.PermitAllUrlProperties;
import com.opc.framework.interceptor.RepeatSubmitInterceptor;
import com.opc.framework.security.filter.JwtAuthenticationTokenFilter;
import com.opc.framework.security.handle.AuthenticationEntryPointImpl;
import com.opc.framework.security.handle.LogoutSuccessHandlerImpl;
import com.opc.framework.web.service.TokenService;

class SwaggerExposureIntegrationTest
{
    @Nested
    @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = { "spring.profiles.active=dev", "token.secret=swagger-test-secret" })
    class Development
    {
        @LocalServerPort
        private int port;

        @Test
        void exposesSwaggerUiAndApiDocs() throws Exception
        {
            assertEquals(200, get(port, "/swagger-ui/index.html").statusCode());
            assertEquals(200, get(port, "/v3/api-docs").statusCode());
        }
    }

    @Nested
    @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "spring.profiles.active=prod",
                    "token.secret=swagger-test-secret",
                    "cors.allowed-origins=http://localhost",
                    "springdoc.api-docs.enabled=false",
                    "springdoc.swagger-ui.enabled=false"
            })
    class Production
    {
        @LocalServerPort
        private int port;

        @Test
        void doesNotExposeSwaggerUiOrApiDocs() throws Exception
        {
            assertNotEquals(200, get(port, "/swagger-ui/index.html").statusCode());
            assertNotEquals(200, get(port, "/swagger-ui.html").statusCode());
            assertNotEquals(200, get(port, "/v3/api-docs").statusCode());
        }
    }

    private static HttpResponse<String> get(int port, String path) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class })
    @Import({ ResourcesConfig.class, SecurityConfig.class })
    static class TestApplication
    {
        @Bean
        RepeatSubmitInterceptor repeatSubmitInterceptor()
        {
            return mock(RepeatSubmitInterceptor.class);
        }

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
    }
}
