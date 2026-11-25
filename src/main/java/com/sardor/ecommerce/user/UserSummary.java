package com.sardor.ecommerce.user;

public record UserSummary(String id, String fullName, String email, String role) {
    public static UserSummary from(UserEntity user) {
        return new UserSummary(user.getId().toString(), user.getFullName(), user.getEmail(), user.getRole().name());
    }
}
