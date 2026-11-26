package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.dtos.request.StackTraceRequest;
import com.snsgos.stacktrace.dtos.response.AnalysisSummaryResponse;
import com.snsgos.stacktrace.dtos.response.DiagnosticReport;
import com.snsgos.stacktrace.dtos.response.PagedResponse;
import com.snsgos.stacktrace.dtos.response.StackTraceResponse;
import com.snsgos.stacktrace.entity.StackTrace;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.exception.ApiException;
import com.snsgos.stacktrace.repository.StackTraceRepository;
import com.snsgos.stacktrace.repository.UserRepository;
import com.snsgos.stacktrace.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StackTraceService {

    private final StackTraceRepository stackTraceRepository;
    private final UserRepository userRepository;
    private final SecretScrubberService secretScrubberService;
    private final DiagnosticAiService diagnosticAiService;
    private final DiagnosticMarkdownFormatter markdownFormatter;
    private final ObjectMapper objectMapper;

    @Transactional
    public StackTraceResponse analyzeAndSave(StackTraceRequest request, UserPrincipal principal, String sessionId) {
        String sanitizedTrace = secretScrubberService.scrub(request.rawStackTrace().trim());
        String sanitizedContext = StringUtils.hasText(request.codeContext())
                ? secretScrubberService.scrub(request.codeContext().trim())
                : null;

        DiagnosticAiService.AiAnalysisResult aiResult = diagnosticAiService.analyze(
                sanitizedTrace,
                request.languageHint(),
                sanitizedContext
        );

        DiagnosticReport report = aiResult.report();
        Map<String, Object> jsonResult = objectMapper.convertValue(report, new TypeReference<>() {});

        User owner = null;
        String effectiveSessionId = null;
        if (principal != null) {
            owner = userRepository.getReferenceById(principal.getId());
        } else {
            effectiveSessionId = sessionId;
        }

        StackTrace entity = StackTrace.builder()
                .user(owner)
                .sessionId(effectiveSessionId)
                .rawStackTrace(sanitizedTrace)
                .codeContext(sanitizedContext)
                .detectedLanguage(truncate(report.detectedLanguage(), 50))
                .errorType(truncate(report.errorType(), 255))
                .severity(report.severity())
                .analysisResult(jsonResult)
                .modelUsed(truncate(aiResult.modelUsed(), 100))
                .build();

        StackTrace saved = stackTraceRepository.save(entity);
        return toDetailedResponse(saved, report, true);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AnalysisSummaryResponse> getHistory(
            String scope,
            String keyword,
            String language,
            int page,
            int size,
            UserPrincipal principal,
            String sessionId
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        String escapedKeyword = escapeLikeKeyword(keyword);
        String normalizedLanguage = StringUtils.hasText(language) && !"all".equalsIgnoreCase(language.trim())
                ? language.trim()
                : "";

        Page<StackTrace> results;
        if ("mine".equalsIgnoreCase(scope)) {
            if (principal != null) {
                results = stackTraceRepository.searchUserHistory(principal.getId(), escapedKeyword, normalizedLanguage, pageable);
            } else if (StringUtils.hasText(sessionId)) {
                results = stackTraceRepository.searchSessionHistory(sessionId, escapedKeyword, normalizedLanguage, pageable);
            } else {
                return new PagedResponse<>(List.of(), safePage, safeSize, 0, 0, false, false);
            }
        } else {
            results = stackTraceRepository.searchAllHistory(escapedKeyword, normalizedLanguage, pageable);
        }

        List<AnalysisSummaryResponse> items = results.getContent().stream()
                .map(entity -> toSummaryResponse(entity, canDelete(entity, principal, sessionId)))
                .toList();

        return new PagedResponse<>(
                items,
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.hasNext(),
                results.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public StackTraceResponse getAnalysis(UUID id, UserPrincipal principal, String sessionId) {
        StackTrace entity = stackTraceRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis record not found"));

        DiagnosticReport report = objectMapper.convertValue(entity.getAnalysisResult(), DiagnosticReport.class);
        return toDetailedResponse(entity, report, canDelete(entity, principal, sessionId));
    }

    @Transactional
    public void deleteAnalysis(UUID id, UserPrincipal principal, String sessionId) {
        StackTrace entity = stackTraceRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis record not found"));

        if (!canDelete(entity, principal, sessionId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to delete this analysis");
        }

        stackTraceRepository.delete(entity);
    }

    private boolean canDelete(StackTrace entity, UserPrincipal principal, String sessionId) {
        if (entity.getUser() != null) {
            return principal != null && entity.getUser().getId().equals(principal.getId());
        }
        return StringUtils.hasText(entity.getSessionId()) && entity.getSessionId().equals(sessionId);
    }

    private StackTraceResponse toDetailedResponse(StackTrace entity, DiagnosticReport report, boolean canDelete) {
        String markdown = markdownFormatter.toMarkdown(report);
        return new StackTraceResponse(
                entity.getId(),
                entity.getRawStackTrace(),
                entity.getCodeContext(),
                entity.getDetectedLanguage(),
                entity.getErrorType(),
                entity.getSeverity(),
                report,
                markdown,
                entity.getModelUsed(),
                entity.getCreatedAt(),
                canDelete
        );
    }

    private AnalysisSummaryResponse toSummaryResponse(StackTrace entity, boolean canDelete) {
        Map<String, Object> result = entity.getAnalysisResult();
        String summary = result != null && result.get("summary") != null
                ? result.get("summary").toString()
                : "";
        String fileName = extractFileName(result);

        return new AnalysisSummaryResponse(
                entity.getId(),
                entity.getDetectedLanguage(),
                entity.getErrorType(),
                entity.getSeverity(),
                summary,
                fileName,
                entity.getModelUsed(),
                entity.getCreatedAt(),
                canDelete
        );
    }

    @SuppressWarnings("unchecked")
    private String extractFileName(Map<String, Object> result) {
        if (result == null) {
            return "Unknown";
        }
        Object locObj = result.get("failureLocation");
        if (locObj instanceof Map<?, ?> loc && loc.get("fileName") != null) {
            return loc.get("fileName").toString();
        }
        return "Unknown";
    }

    private String escapeLikeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
