package com.snsgos.stacktrace;

import com.snsgos.stacktrace.dtos.response.DiagnosticReport;
import com.snsgos.stacktrace.enums.Severity;
import com.snsgos.stacktrace.service.DiagnosticAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    void anonymousSessionAndAuthHistoryFlowsWorkEndToEnd() throws Exception {
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

        // 2. Register and login with username/email + password
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
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("alice_test"))
                .andExpect(jsonPath("$.user.hasPassword").value(true))
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("token").asString();

        // 3. Change password without entering current password
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"updatedPassword99\"}"))
                .andExpect(status().isOk());

        // Login with username and new password
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice_test\",\"password\":\"updatedPassword99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // 4. Create analysis as authenticated user
        MvcResult userCreate = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawStackTrace\":\"TypeError: Cannot read properties of undefined at UserProfile.ts:18\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String userAnalysisId = objectMapper.readTree(userCreate.getResponse().getContentAsString())
                .get("id").asString();

        // 5. All history returns both without exposing user details; search filters properly
        mockMvc.perform(get("/api/v1/analyses")
                        .param("scope", "all")
                        .param("q", "OrderService"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].username").doesNotExist())
                .andExpect(jsonPath("$.items[0].userId").doesNotExist());

        // 6. User history only returns authenticated user's records
        mockMvc.perform(get("/api/v1/analyses")
                        .header("Authorization", "Bearer " + token)
                        .param("scope", "mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(userAnalysisId)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(guestAnalysisId))));

        // 7. Guest session history only returns guest's records
        mockMvc.perform(get("/api/v1/analyses")
                        .header("X-Session-Id", sessionId)
                        .param("scope", "mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(guestAnalysisId)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(userAnalysisId))));

        // 8. Ownership enforcement on delete
        mockMvc.perform(delete("/api/v1/analyses/" + guestAnalysisId)
                        .header("X-Session-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/analyses/" + guestAnalysisId)
                        .header("X-Session-Id", sessionId))
                .andExpect(status().isNoContent());

        // 9. Enforce 100 KB payload limit
        String oversized = "{\"rawStackTrace\":\"" + "x".repeat(103_000) + "\"}";
        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized))
                .andExpect(status().isContentTooLarge());
    }
}
