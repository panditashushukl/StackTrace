package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.dtos.response.DiagnosticReport;
import com.snsgos.stacktrace.enums.Severity;
import com.snsgos.stacktrace.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calls Google Gemini via Spring AI ChatClient with enforced structured JSON output
 * and automatic fallback to secondary Gemini models if the primary model fails.
 */
@Service
@Slf4j
public class DiagnosticAiService {

    public record AiAnalysisResult(DiagnosticReport report, String modelUsed) {}

    private static final String PLACEHOLDER_KEY = "placeholder-gemini-key";
    private static final String TEMPLATE_KEY = "your-gemini-api-key";

    private static final String PDF_DIAGNOSTIC_SCHEMA = """
            {
              "type": "object",
              "required": [
                "detectedLanguage", "errorType", "severity", "summary",
                "rootCause", "failureLocation", "stepByStepFix", "codeFix",
                "preventiveRecommendations"
              ],
              "properties": {
                "detectedLanguage": { "type": "string" },
                "errorType": { "type": "string" },
                "severity": { "type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"] },
                "summary": { "type": "string" },
                "rootCause": { "type": "string" },
                "failureLocation": {
                  "type": "object",
                  "properties": {
                    "fileName": { "type": "string" },
                    "className": { "type": "string" },
                    "methodName": { "type": "string" },
                    "lineNumber": { "type": "integer" }
                  }
                },
                "stepByStepFix": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "codeFix": {
                  "type": "object",
                  "properties": {
                    "language": { "type": "string" },
                    "explanation": { "type": "string" },
                    "snippet": { "type": "string" }
                  }
                },
                "preventiveRecommendations": {
                  "type": "array",
                  "items": { "type": "string" }
                }
              }
            }
            """;

    private static final String SYSTEM_PROMPT = """
            You are StackTrace Buddy, a senior software engineer diagnosing runtime errors and stack traces across Java, Python, JavaScript/TypeScript, Node.js, C#, Go, PHP, and other languages.

            Analyze the provided stack trace, optional language hint, and optional code context.
            Respond strictly with JSON matching the configured schema:
            - detectedLanguage: Detected runtime/language (respect valid languageHint when applicable).
            - errorType: Exact exception or error name (e.g., NullPointerException, TypeError, KeyError).
            - severity: One of LOW, MEDIUM, HIGH, CRITICAL.
            - summary: Clear plain-English summary of what failed.
            - rootCause: Specific technical reason why the failure happened.
            - failureLocation: Pinpoint fileName, className, methodName, and lineNumber (0 if unknown).
            - stepByStepFix: Ordered, practical steps to resolve the bug.
            - codeFix: language, short explanation, and refactored code snippet fixing the root cause.
            - preventiveRecommendations: Best practices to prevent this class of failure.

            If the input is NOT a valid or parseable stack trace or lacks enough context to diagnose:
            Set errorType to "UnparseableStackTrace", severity to "LOW", detectedLanguage to the hint or "Unknown",
            explain in summary and rootCause what is missing, and use stepByStepFix to request the full error message, stack frames, and relevant code snippet.
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<DiagnosticReport> outputConverter;
    private final String apiKey;
    private final List<String> candidateModels;
    private final double temperature;
    private final int maxOutputTokens;

    public DiagnosticAiService(
            ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.google.genai.api-key:}") String apiKey,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String primaryModel,
            @Value("${app.ai.fallback-models:gemini-2.5-flash-lite,gemini-2.0-flash}") List<String> fallbackModels,
            @Value("${spring.ai.google.genai.chat.options.temperature:0.2}") double temperature,
            @Value("${spring.ai.google.genai.chat.options.max-output-tokens:4096}") int maxOutputTokens
    ) {
        this.chatClient = chatClientBuilder.build();
        this.outputConverter = new BeanOutputConverter<>(DiagnosticReport.class);
        this.apiKey = apiKey;
        this.candidateModels = buildCandidateModels(primaryModel, fallbackModels);
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
    }

    public AiAnalysisResult analyze(String sanitizedStackTrace, String languageHint, String sanitizedCodeContext) {
        if (!hasValidApiKey()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini API key is not configured on the backend. Set GEMINI_API_KEY in .env or environment variables."
            );
        }

        String userPrompt = buildUserPrompt(sanitizedStackTrace, languageHint, sanitizedCodeContext);
        Exception lastFailure = null;

        for (int i = 0; i < candidateModels.size(); i++) {
            String targetModel = candidateModels.get(i);
            try {
                return executeWithModel(targetModel, userPrompt, languageHint);
            } catch (Exception ex) {
                lastFailure = ex;
                if (i + 1 < candidateModels.size()) {
                    log.warn("Gemini model '{}' failed ({}). Falling back to '{}'.",
                            targetModel, ex.getMessage(), candidateModels.get(i + 1));
                } else {
                    log.error("Gemini model '{}' failed and no fallback models remain.", targetModel, ex);
                }
            }
        }

        log.error("All Gemini candidate models failed: {}", candidateModels, lastFailure);
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI diagnostic service is temporarily unavailable across primary and fallback models. Please try again."
        );
    }

    private AiAnalysisResult executeWithModel(String modelName, String userPrompt, String languageHint) {
        GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .maxOutputTokens(maxOutputTokens)
                .responseMimeType("application/json")
                .responseSchema(PDF_DIAGNOSTIC_SCHEMA);

        ChatResponse chatResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .options(optionsBuilder)
                .call()
                .chatResponse();

        String content = chatResponse != null && chatResponse.getResult() != null
                ? chatResponse.getResult().getOutput().getText()
                : null;

        if (!StringUtils.hasText(content)) {
            return new AiAnalysisResult(fallbackReport(languageHint), modelName);
        }

        DiagnosticReport parsed = outputConverter.convert(content);
        DiagnosticReport normalized = normalizeReport(parsed, languageHint);
        String modelUsed = resolveModelUsed(chatResponse, modelName);
        return new AiAnalysisResult(normalized, modelUsed);
    }

    private boolean hasValidApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        String trimmed = apiKey.trim();
        return !PLACEHOLDER_KEY.equals(trimmed) && !TEMPLATE_KEY.equals(trimmed);
    }

    private List<String> buildCandidateModels(String primaryModel, List<String> fallbackModels) {
        Set<String> ordered = new LinkedHashSet<>();
        if (StringUtils.hasText(primaryModel)) {
            ordered.add(primaryModel.trim());
        } else {
            ordered.add("gemini-2.5-flash");
        }
        if (fallbackModels != null) {
            for (String candidate : fallbackModels) {
                if (StringUtils.hasText(candidate)) {
                    ordered.add(candidate.trim());
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    private String buildUserPrompt(String stackTrace, String languageHint, String codeContext) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(languageHint) && !"auto".equalsIgnoreCase(languageHint.trim())) {
            sb.append("Language Hint: ").append(languageHint.trim()).append("\n\n");
        }
        sb.append("Stack Trace:\n").append(stackTrace).append("\n");
        if (StringUtils.hasText(codeContext)) {
            sb.append("\nCode Context:\n").append(codeContext).append("\n");
        }
        return sb.toString();
    }

    private String resolveModelUsed(ChatResponse response, String requestedModel) {
        if (response != null && response.getMetadata() != null && StringUtils.hasText(response.getMetadata().getModel())) {
            return response.getMetadata().getModel();
        }
        return requestedModel;
    }

    private DiagnosticReport normalizeReport(DiagnosticReport raw, String languageHint) {
        if (raw == null) {
            return fallbackReport(languageHint);
        }

        String lang = StringUtils.hasText(raw.detectedLanguage())
                ? raw.detectedLanguage().trim()
                : (StringUtils.hasText(languageHint) ? languageHint.trim() : "Unknown");
        String errorType = StringUtils.hasText(raw.errorType()) ? raw.errorType().trim() : "RuntimeError";
        Severity severity = raw.severity() != null ? raw.severity() : Severity.MEDIUM;
        String summary = StringUtils.hasText(raw.summary()) ? raw.summary().trim() : "Analysis completed.";
        String rootCause = StringUtils.hasText(raw.rootCause()) ? raw.rootCause().trim() : "See stack frames and code fix below.";

        DiagnosticReport.FailureLocation loc = raw.failureLocation() != null
                ? new DiagnosticReport.FailureLocation(
                defaultIfBlank(raw.failureLocation().fileName(), "Unknown"),
                defaultIfBlank(raw.failureLocation().className(), "Unknown"),
                defaultIfBlank(raw.failureLocation().methodName(), "Unknown"),
                raw.failureLocation().lineNumber() != null ? raw.failureLocation().lineNumber() : 0
        )
                : new DiagnosticReport.FailureLocation("Unknown", "Unknown", "Unknown", 0);

        List<String> steps = (raw.stepByStepFix() != null && !raw.stepByStepFix().isEmpty())
                ? raw.stepByStepFix()
                : List.of("Review the top application frame in the stack trace and validate inputs.");

        DiagnosticReport.CodeFix fix = raw.codeFix() != null
                ? new DiagnosticReport.CodeFix(
                defaultIfBlank(raw.codeFix().language(), lang),
                defaultIfBlank(raw.codeFix().explanation(), "Suggested defensive fix."),
                defaultIfBlank(raw.codeFix().snippet(), "// Add null/type checks around the failing line.")
        )
                : new DiagnosticReport.CodeFix(lang, "Suggested defensive fix.", "// Add null/type checks around the failing line.");

        List<String> recommendations = (raw.preventiveRecommendations() != null && !raw.preventiveRecommendations().isEmpty())
                ? raw.preventiveRecommendations()
                : List.of("Add unit tests covering edge cases and boundary inputs.");

        return new DiagnosticReport(lang, errorType, severity, summary, rootCause, loc, steps, fix, recommendations);
    }

    private DiagnosticReport fallbackReport(String languageHint) {
        String lang = StringUtils.hasText(languageHint) ? languageHint.trim() : "Unknown";
        return new DiagnosticReport(
                lang,
                "UnparseableStackTrace",
                Severity.LOW,
                "The submitted text does not appear to contain enough stack trace information to diagnose.",
                "No recognizable exception message or stack frames were detected in the response.",
                new DiagnosticReport.FailureLocation("Unknown", "Unknown", "Unknown", 0),
                List.of(
                        "Paste the complete error message and stack frames from your console or logs.",
                        "Optionally include the failing function or snippet in Code Context."
                ),
                new DiagnosticReport.CodeFix(lang, "No code patch generated without a valid stack trace.", "// Provide stack frames to generate a targeted fix."),
                List.of("Include full exception headers and line numbers when reporting errors.")
        );
    }

    private String defaultIfBlank(String val, String fallback) {
        return StringUtils.hasText(val) ? val.trim() : fallback;
    }
}
