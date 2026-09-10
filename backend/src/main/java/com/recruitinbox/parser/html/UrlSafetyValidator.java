package com.recruitinbox.parser.html;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;

/**
 * SSRF guard (v1.1 section 12.1): http/https + ports 80/443 only, no userinfo,
 * and every resolved A/AAAA address must be a public unicast address. Re-run on
 * each redirect hop. {@code parser.fetch.allow-private-hosts=true} relaxes the
 * IP check for local fixtures/tests only.
 */
@Component
public class UrlSafetyValidator {

    private final boolean allowPrivateHosts;

    public UrlSafetyValidator(@Value("${parser.fetch.allow-private-hosts:false}") boolean allowPrivateHosts) {
        this.allowPrivateHosts = allowPrivateHosts;
    }

    public InetAddress[] assertSafe(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ApiException(ErrorCode.UNSAFE_URL, "only http/https is allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new ApiException(ErrorCode.UNSAFE_URL, "URLs with credentials are rejected");
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new ApiException(ErrorCode.UNSAFE_URL, "only ports 80 and 443 are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(ErrorCode.UNSAFE_URL, "url has no host");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ApiException(ErrorCode.UNSAFE_URL, "host does not resolve");
        }
        if (!allowPrivateHosts) {
            for (InetAddress addr : addresses) {
                if (isBlocked(addr)) {
                    throw new ApiException(ErrorCode.UNSAFE_URL,
                            "host resolves to a non-public address: " + addr.getHostAddress());
                }
            }
        }
        return addresses;
    }

    private boolean isBlocked(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int a = b[0] & 0xff;
            int c = b[1] & 0xff;
            if (a == 169 && c == 254) {
                return true; // link-local / cloud metadata 169.254.169.254
            }
            if (a == 100 && c >= 64 && c <= 127) {
                return true; // carrier-grade NAT 100.64.0.0/10
            }
            if (a == 192 && c == 0 && (b[2] & 0xff) == 0) {
                return true; // 192.0.0.0/24
            }
            if (a == 0 || a >= 240) {
                return true; // "this network" / reserved
            }
        } else if (b.length == 16) {
            // IPv4-mapped IPv6 ::ffff:a.b.c.d
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isBlocked(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException ignored) {
                    return true;
                }
            }
            int first = b[0] & 0xff;
            if (first == 0xfc || first == 0xfd) {
                return true; // unique local fc00::/7
            }
        }
        return false;
    }
}
