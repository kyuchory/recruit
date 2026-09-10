package com.recruitinbox.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.common.security.CurrentUserProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final CurrentUserProvider currentUser;
    private final UserIdentityService identities;

    public AuthController(CurrentUserProvider currentUser, UserIdentityService identities) {
        this.currentUser = currentUser;
        this.identities = identities;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return identities.me(currentUser.requireCurrentUserId());
    }

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return Map.of();
        }
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
