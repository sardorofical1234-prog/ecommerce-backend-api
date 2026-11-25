package com.sardor.ecommerce.auth;

import com.sardor.ecommerce.user.UserSummary;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserSummary user
) {
}
