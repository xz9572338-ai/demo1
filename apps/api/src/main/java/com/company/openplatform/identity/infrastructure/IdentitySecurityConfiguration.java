package com.company.openplatform.identity.infrastructure;

import com.company.openplatform.shared.api.ApiError;
import com.company.openplatform.shared.observability.RequestIdFilter;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableRedisHttpSession(redisNamespace = "open-platform:console:session", maxInactiveIntervalInSeconds = 1800)
public class IdentitySecurityConfiguration {
    @Bean @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
            SessionStatusRefreshFilter statusRefreshFilter) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        return http.securityMatcher("/console/api/v1/**", "/actuator/health", "/actuator/health/**").authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/console/api/v1/registration-applications").permitAll()
                        .requestMatchers(HttpMethod.GET, "/console/api/v1/registration-applications/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/console/api/v1/sessions/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/console/api/v1/sessions").permitAll()
                        .requestMatchers("/console/api/v1/session").authenticated()
                        .requestMatchers("/console/api/v1/onboarding/**").authenticated()
                        .anyRequest().hasAuthority("ONBOARDING_APPROVED"))
                .csrf(configurer -> configurer.csrfTokenRepository(csrf))
                .exceptionHandling(configurer -> configurer
                    .authenticationEntryPoint((request, response, exception) -> write(objectMapper, request, response,
                            401, "AUTHENTICATION_REQUIRED", "请先登录"))
                    .accessDeniedHandler((request, response, exception) -> write(objectMapper, request, response,
                            403, "ACCESS_DENIED", "请求被安全策略拒绝")))
                .addFilterAfter(statusRefreshFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<SessionStatusRefreshFilter> disableContainerRegistration(SessionStatusRefreshFilter filter) {
        FilterRegistrationBean<SessionStatusRefreshFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
    }
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean
    CookieSerializer sessionCookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Strict");
        return serializer;
    }

    private static void write(ObjectMapper mapper, jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(code, message,
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE), List.of(), false));
    }
}
