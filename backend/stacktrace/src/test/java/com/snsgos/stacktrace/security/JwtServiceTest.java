package com.snsgos.stacktrace.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 256-bit test secret
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L); // 1 hour
    }

    @Test
    void generateToken_shouldProduceValidTokenWithCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "dev@stacktracebuddy.com";
        String name = "Test Developer";
        String role = "ROLE_USER";

        String token = jwtService.generateToken(userId, email, name, role);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(email, jwtService.extractEmail(token));
    }

    @Test
    void isTokenValid_shouldReturnFalseForTamperedToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "dev@example.com", "Dev", "ROLE_USER");

        String tamperedToken = token + "xyz";
        assertFalse(jwtService.isTokenValid(tamperedToken));
    }
}
