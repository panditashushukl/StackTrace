package com.snsgos.stacktrace.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Issues, reads, and clears HttpOnly browser cookies for access (1 day) and refresh (15 days) tokens.
 */
@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "stb_access_token";
    public static final String REFRESH_TOKEN_COOKIE = "stb_refresh_token";

    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    private final String cookieSecureSetting;
    private final String cookieSameSiteSetting;

    public AuthCookieService(
            @Value("${app.jwt.expiration-ms:86400000}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms:1296000000}") long refreshExpirationMs,
            @Value("${app.cookie.secure:auto}") String cookieSecureSetting,
            @Value("${app.cookie.same-site:auto}") String cookieSameSiteSetting
    ) {
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.cookieSecureSetting = cookieSecureSetting;
        this.cookieSameSiteSetting = cookieSameSiteSetting;
    }

    public void addAuthCookies(HttpServletRequest request, HttpServletResponse response,
                               String accessToken, String refreshToken) {
        boolean secure = isSecureCookie(request);
        String sameSite = resolveSameSite(secure);

        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofMillis(accessExpirationMs))
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public void clearAuthCookies(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = isSecureCookie(request);
        String sameSite = resolveSameSite(secure);

        ResponseCookie clearAccess = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie clearRefresh = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
    }

    public String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isSecureCookie(HttpServletRequest request) {
        if ("true".equalsIgnoreCase(cookieSecureSetting)) {
            return true;
        }
        if ("false".equalsIgnoreCase(cookieSecureSetting)) {
            return false;
        }
        return request != null && request.isSecure();
    }

    private String resolveSameSite(boolean secure) {
        if (!"auto".equalsIgnoreCase(cookieSameSiteSetting) && !cookieSameSiteSetting.isBlank()) {
            return cookieSameSiteSetting;
        }
        // Cross-origin HTTPS deployments (e.g. Vercel -> Render) require SameSite=None + Secure
        return secure ? "None" : "Lax";
    }
}
