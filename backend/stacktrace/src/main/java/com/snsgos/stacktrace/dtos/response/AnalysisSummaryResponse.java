package com.snsgos.stacktrace.dtos.response;

import com.snsgos.stacktrace.enums.Severity;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight history item without user details for safe global and personal history lists.
 */
public record AnalysisSummaryResponse(
        UUID id,
        String detectedLanguage,
        String errorType,
        Severity severity,
        String summary,
        String fileName,
        String modelUsed,
        Instant createdAt,
        boolean canDelete
) {}
