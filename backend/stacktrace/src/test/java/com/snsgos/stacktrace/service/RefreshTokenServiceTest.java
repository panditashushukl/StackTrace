package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.entity.RefreshToken;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.enums.Role;
import com.snsgos.stacktrace.exception.ApiException;
import com.snsgos.stacktrace.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long FIFTEEN_DAYS_MS = 1_296_000_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, FIFTEEN_DAYS_MS);
    }

    @Test
    void createRefreshToken_shouldStoreSha256HashWith15DayExpiry() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("dev@example.com")
                .name("Dev")
                .username("dev")
                .role(Role.ROLE_USER)
                .build();

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        String rawToken = refreshTokenService.createRefreshToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertNotNull(rawToken);
        assertNotEquals(rawToken, saved.getTokenHash());
        assertEquals(RefreshTokenService.sha256Hex(rawToken), saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        assertTrue(saved.getExpiresAt().isAfter(before.plusMillis(FIFTEEN_DAYS_MS - 5000)));
    }

    @Test
    void rotateRefreshToken_shouldRevokeOldAndIssueNewInSameFamily() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").role(Role.ROLE_USER).build();
        UUID familyId = UUID.randomUUID();
        String oldRaw = "raw-token-value";
        RefreshToken existing = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(RefreshTokenService.sha256Hex(oldRaw))
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByTokenHashWithUser(existing.getTokenHash()))
                .thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotateRefreshToken(oldRaw);

        assertNotEquals(oldRaw, rotated.rawToken());
        assertNotNull(existing.getRevokedAt());
        assertEquals(RefreshTokenService.sha256Hex(rotated.rawToken()), existing.getReplacedByTokenHash());

        ArgumentCaptor<RefreshToken> saveCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(saveCaptor.capture());
        RefreshToken replacement = saveCaptor.getAllValues().get(1);
        assertEquals(familyId, replacement.getFamilyId());
        assertNull(replacement.getRevokedAt());
    }

    @Test
    void rotateRefreshToken_shouldRevokeEntireFamilyWhenReused() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").role(Role.ROLE_USER).build();
        UUID familyId = UUID.randomUUID();
        String replayedRaw = "replayed-token";
        RefreshToken alreadyRevoked = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(RefreshTokenService.sha256Hex(replayedRaw))
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(Instant.now().minusSeconds(60))
                .build();

        when(refreshTokenRepository.findByTokenHashWithUser(alreadyRevoked.getTokenHash()))
                .thenReturn(Optional.of(alreadyRevoked));

        ApiException ex = assertThrows(ApiException.class, () ->
                refreshTokenService.rotateRefreshToken(replayedRaw));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void rotateRefreshToken_shouldDeleteExpiredTokenFromDb() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").role(Role.ROLE_USER).build();
        String expiredRaw = "expired-token";
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(RefreshTokenService.sha256Hex(expiredRaw))
                .familyId(UUID.randomUUID())
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        when(refreshTokenRepository.findByTokenHashWithUser(expired.getTokenHash()))
                .thenReturn(Optional.of(expired));

        ApiException ex = assertThrows(ApiException.class, () ->
                refreshTokenService.rotateRefreshToken(expiredRaw));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    void purgeExpiredTokens_shouldDeleteExpiredRows() {
        when(refreshTokenRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(4);

        int deleted = refreshTokenService.purgeExpiredTokens();

        assertEquals(4, deleted);
        verify(refreshTokenRepository).deleteExpiredBefore(any(Instant.class));
    }
}
