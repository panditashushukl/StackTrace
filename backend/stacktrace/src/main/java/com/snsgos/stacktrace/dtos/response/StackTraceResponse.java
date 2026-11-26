package com.snsgos.stacktrace.dtos.response;

import com.snsgos.stacktrace.enums.Severity;

import java.time.Instant;
import java.util.UUID;

public record StackTraceResponse(
        UUID id,
        String rawStackTrace,
        String codeContext,
        String detectedLanguage,
        String errorType,
        Severity severity,
        DiagnosticReport analysisResult,
        String markdownReport,
        String modelUsed,
        Instant createdAt,
        boolean canDelete
) {}
