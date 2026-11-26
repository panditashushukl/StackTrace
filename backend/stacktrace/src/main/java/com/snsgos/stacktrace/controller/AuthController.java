package com.snsgos.stacktrace.controller;

import com.snsgos.stacktrace.dtos.request.LoginRequest;
import com.snsgos.stacktrace.dtos.request.RegisterRequest;
import com.snsgos.stacktrace.dtos.response.AuthResponse;
import com.snsgos.stacktrace.dtos.response.UserProfileResponse;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.security.JwtService;
import com.snsgos.stacktrace.service.UserService;
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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(buildAuthResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request);
        return ResponseEntity.ok(buildAuthResponse(user));
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

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
        UserProfileResponse profile = userService.toProfileResponse(user);
        return new AuthResponse(token, profile);
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
