package com.snsgos.stacktrace.dtos.response;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String name,
        String username,
        String avatarUrl,
        String role,
        boolean hasPassword,
        boolean googleLinked
) {}
