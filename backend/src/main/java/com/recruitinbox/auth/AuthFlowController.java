package com.recruitinbox.auth;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthFlowController {

    static final String RETURN_TO_SESSION_ATTRIBUTE = "recruitInbox.returnTo";
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "kakao");

    private final ObjectProvider<ClientRegistrationRepository> registrations;
    private final String frontendLoginUrl;

    public AuthFlowController(ObjectProvider<ClientRegistrationRepository> registrations,
            @Value("${app.auth.frontend-login-url}") String frontendLoginUrl) {
        this.registrations = registrations;
        this.frontendLoginUrl = frontendLoginUrl;
    }

    @GetMapping("/providers")
    public Map<String, Boolean> providers() {
        return Map.of("google", isAvailable("google"), "kakao", isAvailable("kakao"));
    }

    @GetMapping("/start/{provider}")
    public ResponseEntity<Void> start(@PathVariable String provider,
            @RequestParam(defaultValue = "/app") String returnTo,
            HttpServletRequest request) {
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDERS.contains(normalizedProvider) || !isAvailable(normalizedProvider)) {
            URI location = UriComponentsBuilder.fromUriString(frontendLoginUrl)
                    .queryParam("error", "provider_unavailable")
                    .queryParam("returnTo", safeReturnTo(returnTo))
                    .build().encode().toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
        }

        request.getSession(true).setAttribute(RETURN_TO_SESSION_ATTRIBUTE, safeReturnTo(returnTo));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/" + normalizedProvider)).build();
    }

    static String safeReturnTo(String candidate) {
        if (candidate == null || candidate.isBlank() || !candidate.startsWith("/")
                || candidate.startsWith("//") || candidate.contains("\\")
                || candidate.indexOf('\r') >= 0 || candidate.indexOf('\n') >= 0) {
            return "/app";
        }
        return candidate;
    }

    private boolean isAvailable(String provider) {
        ClientRegistrationRepository repository = registrations.getIfAvailable();
        return repository != null && repository.findByRegistrationId(provider) != null;
    }
}
