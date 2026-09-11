package com.recruitinbox.common.web;

import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CSRF request handler tuned for a JavaScript SPA that reads the token from the
 * {@code XSRF-TOKEN} cookie and echoes it in the {@code X-XSRF-TOKEN} header
 * (see Spring Security reference, "Single Page Applications").
 *
 * <ul>
 *   <li>When the token arrives as a request header (our fetch client) it is the
 *       raw value -&gt; resolve with the plain handler.</li>
 *   <li>When it arrives as a form parameter it is BREACH-masked -&gt; resolve
 *       with the XOR handler.</li>
 * </ul>
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        // Render with XOR masking; the actual token is materialized by CsrfCookieFilter.
        this.xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        boolean fromHeader = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()));
        return (fromHeader ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
