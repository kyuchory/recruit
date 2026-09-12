package com.recruitinbox.common.web;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * If Google OAuth2 is configured (a {@link ClientRegistrationRepository} bean
 * exists) the filter chain enables {@code oauth2Login} and identity comes from
 * the session ({@link com.recruitinbox.auth.SessionCurrentUserProvider}).
 * Otherwise the dev header provider is used.
 *
 * <p>CSRF: cookie-based double-submit for browser mutations. The token is
 * published in a readable {@code XSRF-TOKEN} cookie ({@link CsrfCookieFilter})
 * and must come back in the {@code X-XSRF-TOKEN} header on
 * POST/PUT/PATCH/DELETE ({@link SpaCsrfTokenRequestHandler}). Safe methods and
 * the OAuth2 redirect endpoints are exempt. Failures render as
 * {@code 403 FORBIDDEN} via {@link RestAuthErrorHandler}.
 */
@Configuration
public class SecurityConfig {

    private final List<String> allowedOrigins;
    private final boolean requireAuthentication;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.auth.require-authentication:false}") boolean requireAuthentication) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.requireAuthentication = requireAuthentication;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
            RestAuthErrorHandler authErrorHandler,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<AuthenticationSuccessHandler> loginSuccessHandler) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/oauth2/**", "/login/**"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authErrorHandler)
                        .accessDeniedHandler(authErrorHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/providers",
                            "/api/v1/auth/start/**").permitAll();
                    if (requireAuthentication) {
                        auth.requestMatchers("/api/v1/**").authenticated();
                    } else {
                        auth.requestMatchers("/api/v1/**").permitAll();
                    }
                    auth.requestMatchers("/actuator/health/**", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/oauth2/**", "/login/**").permitAll()
                        .anyRequest().authenticated();
                })
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        if (clientRegistrations.getIfAvailable() != null) {
            AuthenticationSuccessHandler successHandler = loginSuccessHandler.getIfAvailable();
            http.oauth2Login(oauth -> {
                if (successHandler != null) {
                    oauth.successHandler(successHandler);
                }
            });
        }
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
