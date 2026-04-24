package com.retailpulse.config;

import feign.Logger;
import feign.RequestInterceptor;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Objects;

@Slf4j
public class FeignConfig {

    private final Tracer tracer;

    public FeignConfig(Tracer tracer) {
        this.tracer = tracer;
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public RequestInterceptor oauth2BearerForwardingInterceptor() {
        return template -> {

            if (tracer != null && tracer.currentSpan() != null) {
                template.header("X-B3-TraceId", Objects.requireNonNull(tracer.currentSpan()).context().traceId());
                template.header("X-B3-SpanId", Objects.requireNonNull(tracer.currentSpan()).context().spanId());
            }

            String token = extractBearerToken();
            if (token != null && !token.isEmpty()) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                log.debug("Feign request [{} {}] -> Authorization header set with Bearer token (prefix={})",
                        template.method(),
                        template.url(),
                        token.substring(0, Math.min(10, token.length())) + "...");
            } else {
                log.warn("Feign request [{} {}] -> No Bearer token found in SecurityContext",
                        template.method(),
                        template.url());
            }
        };
    }

    private String extractBearerToken() {
        // 1) Preferred: from Spring SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        if (auth instanceof BearerTokenAuthentication) {
            return ((BearerTokenAuthentication) auth).getToken().getTokenValue();
        }

        // 2) Fallback: from the incoming HTTP header (in case custom filters populated it)
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes) {
            String authz = ((org.springframework.web.context.request.ServletRequestAttributes) attrs)
                    .getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authz != null && authz.startsWith("Bearer ")) {
                return authz.substring("Bearer ".length());
            }
        }

        // No token available (e.g., internal call, scheduler, or unauthenticated endpoint)
        return null;
    }

}
