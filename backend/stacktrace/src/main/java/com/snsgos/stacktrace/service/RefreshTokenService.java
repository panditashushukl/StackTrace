package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.entity.RefreshToken;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.exception.ApiException;
import com.snsgos.stacktrace.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages opaque refresh tokens with SHA-256 DB storage, single-use rotation,
 * reuse detection, and automatic deletion of expired tokens.
 */
@Service
@Slf4j
public class RefreshTokenService {

    public record IssuedRefreshToken(String rawToken, User user) {}

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-ms:1296000000}") long refreshExpirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Transactional
    public String createRefreshToken(User user) {
        return createTokenInFamily(user, UUID.randomUUID());
    }

    /**
     * Validates an incoming raw refresh token, revokes it, and issues a rotated replacement.
     * Deletes expired tokens immediately and revokes the entire token family if a rotated token is replayed.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedRefreshToken rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }

        String tokenHash = sha256Hex(rawToken.trim());
        RefreshToken existing = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        Instant now = Instant.now();

        if (existing.getExpiresAt().isBefore(now)) {
            refreshTokenRepository.delete(existing);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        if (existing.getRevokedAt() != null) {
            log.warn("Detected reuse of revoked refresh token for user={}, revoking family={}",
                    existing.getUser().getId(), existing.getFamilyId());
            refreshTokenRepository.revokeFamily(existing.getFamilyId(), now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has already been used");
        }

        String nextRawToken = generateRandomToken();
        String nextHash = sha256Hex(nextRawToken);

        existing.setRevokedAt(now);
        existing.setReplacedByTokenHash(nextHash);
        refreshTokenRepository.save(existing);

        RefreshToken replacement = RefreshToken.builder()
                .user(existing.getUser())
                .tokenHash(nextHash)
                .familyId(existing.getFamilyId())
                .expiresAt(now.plusMillis(refreshExpirationMs))
                .build();
        refreshTokenRepository.save(replacement);

        return new IssuedRefreshToken(nextRawToken, existing.getUser());
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = sha256Hex(rawToken.trim());
        refreshTokenRepository.findByTokenHashWithUser(tokenHash).ifPresent(token ->
                refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now())
        );
    }

    /**
     * Deletes all expired refresh tokens at startup and on a daily schedule.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${app.jwt.refresh-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public int purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s) from database", deleted);
        }
        return deleted;
    }

    private String createTokenInFamily(User user, UUID familyId) {
        String rawToken = generateRandomToken();
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .familyId(familyId)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
