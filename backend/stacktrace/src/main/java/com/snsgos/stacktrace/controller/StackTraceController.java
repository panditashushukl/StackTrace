package com.snsgos.stacktrace.controller;

import com.snsgos.stacktrace.dtos.request.StackTraceRequest;
import com.snsgos.stacktrace.dtos.response.AnalysisSummaryResponse;
import com.snsgos.stacktrace.dtos.response.PagedResponse;
import com.snsgos.stacktrace.dtos.response.StackTraceResponse;
import com.snsgos.stacktrace.security.UserPrincipal;
import com.snsgos.stacktrace.service.StackTraceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
public class StackTraceController {

    public static final String SESSION_HEADER = "X-Session-Id";

    private final StackTraceService stackTraceService;

    @PostMapping
    public ResponseEntity<StackTraceResponse> createAnalysis(
            @Valid @RequestBody StackTraceRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = SESSION_HEADER, required = false) String requestedSessionId,
            HttpServletResponse response
    ) {
        String sessionId = null;
        if (principal == null) {
            sessionId = resolveOrGenerateSessionId(requestedSessionId);
            response.setHeader(SESSION_HEADER, sessionId);
        }

        StackTraceResponse result = stackTraceService.analyzeAndSave(request, principal, sessionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<AnalysisSummaryResponse>> getAnalyses(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = SESSION_HEADER, required = false) String requestedSessionId
    ) {
        String sessionId = parseValidSessionId(requestedSessionId);
        PagedResponse<AnalysisSummaryResponse> history = stackTraceService.getHistory(
                scope, q, language, page, size, principal, sessionId
        );
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StackTraceResponse> getAnalysisById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = SESSION_HEADER, required = false) String requestedSessionId
    ) {
        String sessionId = parseValidSessionId(requestedSessionId);
        return ResponseEntity.ok(stackTraceService.getAnalysis(id, principal, sessionId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAnalysis(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = SESSION_HEADER, required = false) String requestedSessionId
    ) {
        String sessionId = parseValidSessionId(requestedSessionId);
        stackTraceService.deleteAnalysis(id, principal, sessionId);
        return ResponseEntity.noContent().build();
    }

    private String resolveOrGenerateSessionId(String candidate) {
        String parsed = parseValidSessionId(candidate);
        return parsed != null ? parsed : UUID.randomUUID().toString();
    }

    private String parseValidSessionId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(candidate.trim()).toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
