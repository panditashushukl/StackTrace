package com.snsgos.stacktrace.controller;

import com.snsgos.stacktrace.dtos.request.LoginRequest;
import com.snsgos.stacktrace.dtos.request.RegisterRequest;
import com.snsgos.stacktrace.dtos.response.AuthResponse;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.exception.ApiException;
import com.snsgos.stacktrace.security.AuthCookieService;
import com.snsgos.stacktrace.security.JwtService;
import com.snsgos.stacktrace.service.RefreshTokenService;
import com.snsgos.stacktrace.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        User user = userService.register(request);
        issueSessionCookies(user, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(userService.toProfileResponse(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        User user = userService.authenticate(request);
        issueSessionCookies(user, httpRequest, httpResponse);
        return ResponseEntity.ok(new AuthResponse(userService.toProfileResponse(user)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String rawRefreshToken = authCookieService.extractCookie(httpRequest, AuthCookieService.REFRESH_TOKEN_COOKIE);
        try {
            RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotateRefreshToken(rawRefreshToken);
            User user = rotated.user();
            String newAccessToken = jwtService.generateToken(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole().name()
            );
            authCookieService.addAuthCookies(httpRequest, httpResponse, newAccessToken, rotated.rawToken());
            return ResponseEntity.ok(new AuthResponse(userService.toProfileResponse(user)));
        } catch (ApiException ex) {
            authCookieService.clearAuthCookies(httpRequest, httpResponse);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String rawRefreshToken = authCookieService.extractCookie(httpRequest, AuthCookieService.REFRESH_TOKEN_COOKIE);
        refreshTokenService.revokeByRawToken(rawRefreshToken);
        authCookieService.clearAuthCookies(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> initSession(
            @RequestHeader(value = "X-Session-Id", required = false) String requestedSessionId,
            HttpServletResponse response
    ) {
        String sessionId = normalizeOrGenerateSessionId(requestedSessionId);
        response.setHeader("X-Session-Id", sessionId);
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    private void issueSessionCookies(User user, HttpServletRequest request, HttpServletResponse response) {
        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
        String refreshToken = refreshTokenService.createRefreshToken(user);
        authCookieService.addAuthCookies(request, response, accessToken, refreshToken);
    }

    private String normalizeOrGenerateSessionId(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Generate fresh UUID if invalid
            }
        }
        return UUID.randomUUID().toString();
    }
}
