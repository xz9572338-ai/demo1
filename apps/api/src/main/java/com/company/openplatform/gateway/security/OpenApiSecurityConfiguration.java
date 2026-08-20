package com.company.openplatform.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class OpenApiSecurityConfiguration {
    @Bean @Order(1)
    SecurityFilterChain sandboxSecurity(HttpSecurity http,OpenApiSecurityFilter filter)throws Exception{
        return http.securityMatcher("/sandbox/v1/**")
                .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context->context.requireExplicitSave(true))
                .csrf(csrf->csrf.disable())
                .requestCache(cache->cache.disable())
                .authorizeHttpRequests(auth->auth.anyRequest().hasAuthority("OPENAPI_AUTHENTICATED"))
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class).build();
    }
    @Bean @Order(99)
    SecurityFilterChain denyAllFallback(HttpSecurity http)throws Exception{
        return http.securityMatcher("/**").csrf(csrf->csrf.disable()).authorizeHttpRequests(auth->auth.anyRequest().denyAll()).build();
    }
}
