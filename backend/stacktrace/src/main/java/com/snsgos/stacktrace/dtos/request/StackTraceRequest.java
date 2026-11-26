package com.snsgos.stacktrace.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StackTraceRequest(

        @NotBlank(message = "Stack trace cannot be empty")
        @Size(max = 50000, message = "Stack trace cannot exceed 50,000 characters")
        String rawStackTrace,

        @Size(max = 50, message = "Language hint is too long")
        String languageHint, // Optional (nullable)

        @Size(max = 10000, message = "Code context cannot exceed 10,000 characters")
        String codeContext   // Optional (nullable)
) {}