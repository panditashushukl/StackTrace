package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.enums.Severity;
import com.snsgos.stacktrace.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DiagnosticAiServiceTest {

    private static final String SAMPLE_JSON = """
            {
              "detectedLanguage": "Java",
              "errorType": "NullPointerException",
              "severity": "HIGH",
              "summary": "Dereferenced a null User object.",
              "rootCause": "userRepository.findById returned empty and was accessed without checking.",
              "failureLocation": {
                "fileName": "OrderService.java",
                "className": "OrderService",
                "methodName": "createOrder",
                "lineNumber": 42
              },
              "stepByStepFix": ["Check Optional before calling get()"],
              "codeFix": {
                "language": "java",
                "explanation": "Use orElseThrow instead of null dereference.",
                "snippet": "User user = userRepository.findById(id).orElseThrow();"
              },
              "preventiveRecommendations": ["Enable nullness annotations"]
            }
            """;

    @Test
    void analyze_shouldThrowServiceUnavailableWhenKeyIsPlaceholder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));

        DiagnosticAiService service = new DiagnosticAiService(
                builder,
                "placeholder-gemini-key",
                "gemini-2.5-flash",
                List.of("gemini-2.5-flash-lite"),
                0.2,
                4096
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                service.analyze("NullPointerException at App.java:10", "Java", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void analyze_shouldParseStructuredJsonResponseFromGemini() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(SAMPLE_JSON))),
                ChatResponseMetadata.builder().model("gemini-2.5-flash").build()
        );

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .chatResponse()).thenReturn(response);

        DiagnosticAiService service = new DiagnosticAiService(
                builder,
                "real-looking-key",
                "gemini-2.5-flash",
                List.of("gemini-2.5-flash-lite"),
                0.2,
                4096
        );
        DiagnosticAiService.AiAnalysisResult result = service.analyze("java.lang.NullPointerException", "Java", null);

        assertEquals("Java", result.report().detectedLanguage());
        assertEquals("NullPointerException", result.report().errorType());
        assertEquals(Severity.HIGH, result.report().severity());
        assertEquals("OrderService.java", result.report().failureLocation().fileName());
        assertEquals(42, result.report().failureLocation().lineNumber());
        assertEquals("gemini-2.5-flash", result.modelUsed());
    }

    @Test
    void analyze_shouldFallbackToSecondaryModelWhenPrimaryFails() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        ChatResponse fallbackResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(SAMPLE_JSON))),
                ChatResponseMetadata.builder().model("gemini-2.5-flash-lite").build()
        );

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .chatResponse())
                .thenThrow(new RuntimeException("429 Resource Exhausted"))
                .thenReturn(fallbackResponse);

        DiagnosticAiService service = new DiagnosticAiService(
                builder,
                "real-looking-key",
                "gemini-2.5-flash",
                List.of("gemini-2.5-flash-lite", "gemini-2.0-flash"),
                0.2,
                4096
        );

        DiagnosticAiService.AiAnalysisResult result = service.analyze("java.lang.NullPointerException", "Java", null);

        assertEquals("gemini-2.5-flash-lite", result.modelUsed());
        assertEquals("NullPointerException", result.report().errorType());
    }

    @Test
    void analyze_shouldThrow503WhenAllModelsFail() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .chatResponse())
                .thenThrow(new RuntimeException("503 Unavailable"));

        DiagnosticAiService service = new DiagnosticAiService(
                builder,
                "real-looking-key",
                "gemini-2.5-flash",
                List.of("gemini-2.5-flash-lite"),
                0.2,
                4096
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                service.analyze("java.lang.NullPointerException", "Java", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }
}
