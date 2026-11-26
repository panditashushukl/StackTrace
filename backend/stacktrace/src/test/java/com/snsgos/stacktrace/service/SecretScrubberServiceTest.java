package com.snsgos.stacktrace.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretScrubberServiceTest {

    private final SecretScrubberService scrubber = new SecretScrubberService();

    @Test
    void scrub_shouldMaskTokensApiKeysDbUrlsAndPasswords() {
        String raw = """
                Caused by: org.postgresql.util.PSQLException: FATAL: password authentication failed
                jdbc:postgresql://admin:SuperSecret123@prod-db.internal:5432/appdb
                Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMifQ.signature123
                api_key=AIzaSyDummyKey1234567890123456789012345
                password="my-db-password"
                """;

        String sanitized = scrubber.scrub(raw);

        assertFalse(sanitized.contains("SuperSecret123"));
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
        assertFalse(sanitized.contains("AIzaSyDummyKey1234567890123456789012345"));
        assertFalse(sanitized.contains("my-db-password"));
        assertTrue(sanitized.contains("jdbc:postgresql://[REDACTED]:[REDACTED]@prod-db.internal:5432/appdb"));
        assertTrue(sanitized.contains("Bearer [REDACTED]"));
    }
}
