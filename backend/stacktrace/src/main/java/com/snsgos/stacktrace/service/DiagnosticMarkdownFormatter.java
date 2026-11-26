package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.dtos.response.DiagnosticReport;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Formats a structured DiagnosticReport into clean Markdown for UI rendering and export.
 */
@Component
public class DiagnosticMarkdownFormatter {

    public String toMarkdown(DiagnosticReport report) {
        if (report == null) {
            return "";
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(safe(report.errorType(), "Diagnostic Report")).append("\n\n");
        md.append("**Language:** `").append(safe(report.detectedLanguage(), "Unknown")).append("` | ");
        md.append("**Severity:** `").append(report.severity() != null ? report.severity().name() : "MEDIUM").append("`\n\n");

        md.append("## Summary\n");
        md.append(safe(report.summary(), "No summary provided.")).append("\n\n");

        md.append("## Root Cause\n");
        md.append(safe(report.rootCause(), "Unable to determine root cause from the provided input.")).append("\n\n");

        if (report.failureLocation() != null) {
            DiagnosticReport.FailureLocation loc = report.failureLocation();
            md.append("## Failure Location\n");
            md.append("- **File:** `").append(safe(loc.fileName(), "N/A")).append("`\n");
            md.append("- **Class:** `").append(safe(loc.className(), "N/A")).append("`\n");
            md.append("- **Method:** `").append(safe(loc.methodName(), "N/A")).append("`\n");
            md.append("- **Line:** `").append(loc.lineNumber() != null && loc.lineNumber() > 0 ? loc.lineNumber() : "N/A").append("`\n\n");
        }

        List<String> steps = report.stepByStepFix();
        if (steps != null && !steps.isEmpty()) {
            md.append("## Step-by-Step Resolution\n");
            for (int i = 0; i < steps.size(); i++) {
                md.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
            md.append("\n");
        }

        if (report.codeFix() != null) {
            DiagnosticReport.CodeFix fix = report.codeFix();
            md.append("## Actionable Code Patch\n");
            if (fix.explanation() != null && !fix.explanation().isBlank()) {
                md.append(fix.explanation().trim()).append("\n\n");
            }
            if (fix.snippet() != null && !fix.snippet().isBlank()) {
                String lang = normalizeFenceLang(fix.language(), report.detectedLanguage());
                md.append("```").append(lang).append("\n");
                md.append(fix.snippet().stripTrailing()).append("\n");
                md.append("```\n\n");
            }
        }

        List<String> recommendations = report.preventiveRecommendations();
        if (recommendations != null && !recommendations.isEmpty()) {
            md.append("## Preventive Best Practices\n");
            for (String item : recommendations) {
                md.append("- ").append(item).append("\n");
            }
        }

        return md.toString().trim();
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String normalizeFenceLang(String codeLang, String detectedLang) {
        String raw = (codeLang != null && !codeLang.isBlank()) ? codeLang : detectedLang;
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "c#", "csharp" -> "csharp";
            case "node.js", "nodejs", "js" -> "javascript";
            case "ts" -> "typescript";
            case "py" -> "python";
            case "golang" -> "go";
            case "unknown", "n/a", "text" -> "";
            default -> normalized.replaceAll("[^a-z0-9_-]", "");
        };
    }
}
