package com.retailpulse.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.authority.AuthorityUtils.NO_AUTHORITIES;

class FeignConfigTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void feignLoggerLevel_returnsFull() {
        FeignConfig feignConfig = new FeignConfig(Mockito.mock(Tracer.class));

        assertEquals(Logger.Level.FULL, feignConfig.feignLoggerLevel());
    }

    @Test
    void oauth2BearerForwardingInterceptor_addsTraceHeadersAndJwtToken() {
        Tracer tracer = Mockito.mock(Tracer.class);
        Span span = Mockito.mock(Span.class);
        TraceContext traceContext = Mockito.mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-id");
        when(traceContext.spanId()).thenReturn("span-id");

        Jwt jwt = Jwt.withTokenValue("jwt-token-value")
                .header("alg", "none")
                .claim("sub", "cashier")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        RequestInterceptor interceptor = new FeignConfig(tracer).oauth2BearerForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/inventory");

        interceptor.apply(template);

        assertHeaderValue(template, "X-B3-TraceId", "trace-id");
        assertHeaderValue(template, "X-B3-SpanId", "span-id");
        assertHeaderValue(template, HttpHeaders.AUTHORIZATION, "Bearer jwt-token-value");
    }

    @Test
    void oauth2BearerForwardingInterceptor_readsBearerTokenAuthentication() {
        Tracer tracer = Mockito.mock(Tracer.class);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "opaque-token",
                Instant.now(),
                Instant.now().plusSeconds(60)
        );
        BearerTokenAuthentication authentication = new BearerTokenAuthentication(
                new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", "cashier"), NO_AUTHORITIES),
                accessToken,
                NO_AUTHORITIES
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        RequestInterceptor interceptor = new FeignConfig(tracer).oauth2BearerForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("POST");
        template.uri("/payment");

        interceptor.apply(template);

        assertHeaderValue(template, HttpHeaders.AUTHORIZATION, "Bearer opaque-token");
        assertFalse(template.headers().containsKey("X-B3-TraceId"));
    }

    @Test
    void oauth2BearerForwardingInterceptor_fallsBackToIncomingAuthorizationHeader() {
        Tracer tracer = Mockito.mock(Tracer.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer forwarded-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInterceptor interceptor = new FeignConfig(tracer).oauth2BearerForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("PUT");
        template.uri("/sales");

        interceptor.apply(template);

        assertHeaderValue(template, HttpHeaders.AUTHORIZATION, "Bearer forwarded-token");
    }

    @Test
    void oauth2BearerForwardingInterceptor_leavesAuthorizationUnsetWhenNoTokenExists() {
        Tracer tracer = Mockito.mock(Tracer.class);
        RequestInterceptor interceptor = new FeignConfig(tracer).oauth2BearerForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("DELETE");
        template.uri("/sales/1");

        interceptor.apply(template);

        assertTrue(template.headers().getOrDefault(HttpHeaders.AUTHORIZATION, List.of()).isEmpty());
    }

    private void assertHeaderValue(RequestTemplate template, String name, String expectedValue) {
        Collection<String> values = template.headers().get(name);
        assertEquals(List.of(expectedValue), values == null ? List.of() : List.copyOf(values));
    }
}
