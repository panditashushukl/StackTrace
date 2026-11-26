package com.snsgos.stacktrace.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Scrubs sensitive secrets (tokens, API keys, DB credentials, passwords, private keys)
 * before sending payloads to Gemini or storing them in PostgreSQL.
 */
@Service
public class SecretScrubberService {

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DB_URL_CREDS_PATTERN = Pattern.compile(
            "((?:jdbc:)?(?:postgres(?:ql)?|mysql|mariadb|mongodb(?:\\+srv)?|rediss?|amqps?)://)([^:/@\\s]+):([^@\\s]+)@",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9\\-._~+/]+=*"
    );

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"
    );

    private static final Pattern PROVIDER_KEY_PATTERN = Pattern.compile(
            "\\b(?:AIza[0-9A-Za-z\\-_]{35}|sk-[A-Za-z0-9\\-_]{20,}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9\\-]+|AKIA[0-9A-Z]{16})\\b"
    );

    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile(
            "(?i)(\\b(?:password|passwd|pwd|secret|api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|auth[_-]?token|client[_-]?secret|private[_-]?key)\\b\\s*[:=]\\s*)([\"']?)([^\\s,;'\")\\]\\}\\n\\r]+)(\\2)"
    );

    private static final Pattern QUERY_PARAM_SECRET_PATTERN = Pattern.compile(
            "(?i)([?&](?:api_key|apikey|token|access_token|secret|password|passwd|key)=)[^&\\s]+"
    );

    public String scrub(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String sanitized = PRIVATE_KEY_PATTERN.matcher(input).replaceAll("[REDACTED_PRIVATE_KEY]");
        sanitized = DB_URL_CREDS_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]:[REDACTED]@");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("[REDACTED_JWT]");
        sanitized = PROVIDER_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_API_KEY]");
        sanitized = KEY_VALUE_SECRET_PATTERN.matcher(sanitized).replaceAll("$1$2[REDACTED]$4");
        sanitized = QUERY_PARAM_SECRET_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        return sanitized;
    }
}
