package com.snsgos.stacktrace;

import com.snsgos.stacktrace.dtos.response.DiagnosticReport;
import com.snsgos.stacktrace.entity.RefreshToken;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.enums.Severity;
import com.snsgos.stacktrace.repository.RefreshTokenRepository;
import com.snsgos.stacktrace.repository.UserRepository;
import com.snsgos.stacktrace.security.AuthCookieService;
import com.snsgos.stacktrace.service.DiagnosticAiService;
import com.snsgos.stacktrace.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StacktraceApplicationTests {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private DiagnosticAiService diagnosticAiService;

    @BeforeEach
    void mockAi() {
        DiagnosticReport report = new DiagnosticReport(
                "Java",
                "NullPointerException",
                Severity.HIGH,
                "Null order reference in OrderService",
                "Repository lookup returned null and was dereferenced without a guard.",
                new DiagnosticReport.FailureLocation("OrderService.java", "OrderService", "process", 42),
                List.of("Check Optional.isPresent() or use orElseThrow()."),
                new DiagnosticReport.CodeFix("java", "Guard null return value.", "Order order = repo.findById(id).orElseThrow();"),
                List.of("Return Optional from repository methods.")
        );
        when(diagnosticAiService.analyze(any(), any(), any()))
                .thenReturn(new DiagnosticAiService.AiAnalysisResult(report, "gemini-2.5-flash"));
    }

    @Test
    void anonymousSessionCookieAuthAndRefreshRotationFlowsWorkEndToEnd() throws Exception {
        // 1. Anonymous analysis creates a session ID and scrubs secrets
        String guestPayload = """
                {
                  "rawStackTrace": "java.lang.NullPointerException at OrderService.java:42 Bearer secretToken12345",
                  "languageHint": "Java",
                  "codeContext": "api_key=AIzaSyDummyKey1234567890123456789012345"
                }
                """;

        MvcResult guestCreate = mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestPayload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Session-Id"))
                .andExpect(jsonPath("$.errorType").value("NullPointerException"))
                .andExpect(jsonPath("$.markdownReport", containsString("## Actionable Code Patch")))
                .andExpect(jsonPath("$.rawStackTrace", not(containsString("secretToken12345"))))
                .andExpect(jsonPath("$.canDelete").value(true))
                .andReturn();

        String sessionId = guestCreate.getResponse().getHeader("X-Session-Id");
        assertNotNull(sessionId);
        JsonNode guestJson = objectMapper.readTree(guestCreate.getResponse().getContentAsString());
        String guestAnalysisId = guestJson.get("id").asString();

        // 2. Register sets 1-day access cookie and 15-day refresh cookie, storing hashed refresh token in DB
        String registerBody = """
                {
                  "name": "Alice Tester",
                  "username": "alice_test",
                  "email": "alice@example.com",
                  "password": "initialPassword1"
                }
                """;

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("alice_test"))
                .andExpect(jsonPath("$.user.hasPassword").value(true))
                .andReturn();

        List<String> registerSetCookies = registerResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertTrue(registerSetCookies.stream().anyMatch(c -> c.contains("stb_access_token=") && c.contains("Max-Age=86400") && c.contains("HttpOnly")));
        assertTrue(registerSetCookies.stream().anyMatch(c -> c.contains("stb_refresh_token=") && c.contains("Max-Age=1296000") && c.contains("HttpOnly")));

        Cookie accessCookie = registerResult.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie refreshCookie = registerResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        assertNotNull(accessCookie);
        assertNotNull(refreshCookie);

        // Raw refresh token must never be stored in DB
        List<RefreshToken> storedTokens = refreshTokenRepository.findAll();
        assertFalse(storedTokens.isEmpty());
        assertTrue(storedTokens.stream().noneMatch(t -> t.getTokenHash().equals(refreshCookie.getValue())));

        // 3. Verify GET /api/v1/users/me and password update using HttpOnly access cookie
        mockMvc.perform(get("/api/v1/users/me")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_test"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"updatedPassword99\"}"))
                .andExpect(status().isOk());

        // Login with username and new password issues fresh cookies
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice_test\",\"password\":\"updatedPassword99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("alice_test"))
                .andReturn();

        Cookie loginAccess = loginResult.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie loginRefresh = loginResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        assertNotNull(loginAccess);
        assertNotNull(loginRefresh);

        // 4. Refresh endpoint rotates BOTH access and refresh tokens
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(loginRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("alice_test"))
                .andReturn();

        Cookie rotatedAccess = refreshResult.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie rotatedRefresh = refreshResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        assertNotNull(rotatedAccess);
        assertNotNull(rotatedRefresh);
        assertNotEquals(loginRefresh.getValue(), rotatedRefresh.getValue());

        // Replaying the already-rotated refresh token must fail with 401 and revoke the family
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(loginRefresh))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rotatedRefresh))
                .andExpect(status().isUnauthorized());

        // 5. Expired/invalid access cookie on /api/v1/analyses triggers 401 so frontend can refresh
        mockMvc.perform(get("/api/v1/analyses")
                        .cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "invalid.jwt.token"))
                        .param("scope", "mine"))
                .andExpect(status().isUnauthorized());

        // 6. Re-login and create analysis with rotated/valid access cookie
        MvcResult relogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice_test\",\"password\":\"updatedPassword99\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie validAccess = relogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie validRefresh = relogin.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        assertNotNull(validAccess);
        assertNotNull(validRefresh);

        MvcResult userCreate = mockMvc.perform(post("/api/v1/analyses")
                        .cookie(validAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawStackTrace\":\"TypeError: Cannot read properties of undefined at UserProfile.ts:18\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String userAnalysisId = objectMapper.readTree(userCreate.getResponse().getContentAsString())
                .get("id").asString();

        // 7. All history returns both without exposing user details; search filters properly
        mockMvc.perform(get("/api/v1/analyses")
                        .param("scope", "all")
                        .param("q", "OrderService"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].username").doesNotExist())
                .andExpect(jsonPath("$.items[0].userId").doesNotExist());

        // 8. User history only returns authenticated user's records
        mockMvc.perform(get("/api/v1/analyses")
                        .cookie(validAccess)
                        .param("scope", "mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(userAnalysisId)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(guestAnalysisId))));

        // 9. Logout clears cookies and revokes refresh token
        MvcResult logoutRes = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(validRefresh))
                .andExpect(status().isNoContent())
                .andReturn();
        assertTrue(logoutRes.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(c -> c.contains("stb_refresh_token=") && c.contains("Max-Age=0")));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(validRefresh))
                .andExpect(status().isUnauthorized());

        // 9b. Expired refresh tokens are deleted from DB both on use and on scheduled purge
        User alice = userRepository.findByUsernameIgnoreCase("alice_test").orElseThrow();
        RefreshToken expired1 = refreshTokenRepository.save(RefreshToken.builder()
                .user(alice)
                .tokenHash("a".repeat(64))
                .familyId(UUID.randomUUID())
                .expiresAt(java.time.Instant.now().minusSeconds(120))
                .build());
        RefreshToken expired2 = refreshTokenRepository.save(RefreshToken.builder()
                .user(alice)
                .tokenHash("b".repeat(64))
                .familyId(UUID.randomUUID())
                .expiresAt(java.time.Instant.now().minusSeconds(120))
                .build());

        assertTrue(refreshTokenRepository.existsById(expired1.getId()));
        assertTrue(refreshTokenRepository.existsById(expired2.getId()));

        int purged = refreshTokenService.purgeExpiredTokens();
        assertTrue(purged >= 2);
        assertFalse(refreshTokenRepository.existsById(expired1.getId()));
        assertFalse(refreshTokenRepository.existsById(expired2.getId()));

        // 10. Guest session history & ownership enforcement
        mockMvc.perform(get("/api/v1/analyses")
                        .header("X-Session-Id", sessionId)
                        .param("scope", "mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(guestAnalysisId)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(userAnalysisId))));

        mockMvc.perform(delete("/api/v1/analyses/" + guestAnalysisId)
                        .header("X-Session-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/analyses/" + guestAnalysisId)
                        .header("X-Session-Id", sessionId))
                .andExpect(status().isNoContent());

        // 11. Enforce 100 KB payload limit
        String oversized = "{\"rawStackTrace\":\"" + "x".repeat(103_000) + "\"}";
        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized))
                .andExpect(status().isContentTooLarge());
    }
}
