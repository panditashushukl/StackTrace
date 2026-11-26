package com.snsgos.stacktrace.dtos.response;

public record AuthResponse(
        String token,
        UserProfileResponse user
) {}
