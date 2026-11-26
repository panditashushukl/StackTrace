package com.snsgos.stacktrace.dtos.response;

import java.time.Instant;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp
) {}
