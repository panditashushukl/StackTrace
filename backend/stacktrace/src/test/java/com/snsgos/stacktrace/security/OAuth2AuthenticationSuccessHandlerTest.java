package com.snsgos.stacktrace.security;

import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.enums.Role;
import com.snsgos.stacktrace.service.RefreshTokenService;
import com.snsgos.stacktrace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    private JwtService jwtService;
    private AuthCookieService authCookieService;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 86_400_000L);

        authCookieService = new AuthCookieService(86_400_000L, 1_296_000_000L, "auto", "auto");

        handler = new OAuth2AuthenticationSuccessHandler(jwtService, userService, refreshTokenService, authCookieService);
        ReflectionTestUtils.setField(handler, "redirectUri", "http://localhost:4200/auth/callback");
    }

    @Test
    void onAuthenticationSuccess_shouldSetAccessAndRefreshCookiesAndRedirectCleanly() throws Exception {
        OidcIdToken idToken = new OidcIdToken(
                "mock-id-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("sub", "google-sub-1", "email", "dev@example.com", "name", "Dev User")
        );
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken
        );

        User saved = User.builder()
                .id(UUID.randomUUID())
                .googleId("google-sub-1")
                .email("dev@example.com")
                .name("Dev User")
                .username("dev")
                .role(Role.ROLE_USER)
                .build();
        when(userService.processOAuthPostLogin(any())).thenReturn(saved);
        when(refreshTokenService.createRefreshToken(saved)).thenReturn("opaque-refresh-token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken(oidcUser, null));

        assertEquals("http://localhost:4200/auth/callback", response.getRedirectedUrl());

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, cookies.size());
        assertTrue(cookies.get(0).contains("stb_access_token="));
        assertTrue(cookies.get(0).contains("Max-Age=86400"));
        assertTrue(cookies.get(0).contains("HttpOnly"));

        assertTrue(cookies.get(1).contains("stb_refresh_token=opaque-refresh-token"));
        assertTrue(cookies.get(1).contains("Max-Age=1296000"));
        assertTrue(cookies.get(1).contains("HttpOnly"));
    }
}
