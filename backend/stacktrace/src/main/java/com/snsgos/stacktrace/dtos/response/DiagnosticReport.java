package com.snsgos.stacktrace.dtos.response;

import com.snsgos.stacktrace.enums.Severity;

import java.util.List;

/**
 * Structured diagnostic report schema enforced for Gemini JSON output.
 */
public record DiagnosticReport(
        String detectedLanguage,
        String errorType,
        Severity severity,
        String summary,
        String rootCause,
        FailureLocation failureLocation,
        List<String> stepByStepFix,
        CodeFix codeFix,
        List<String> preventiveRecommendations
) {
    public record FailureLocation(
            String fileName,
            String className,
            String methodName,
            Integer lineNumber
    ) {}

    public record CodeFix(
            String language,
            String explanation,
            String snippet
    ) {}
}
