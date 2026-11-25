package com.sardor.ecommerce.user;

import java.util.List;

public record CurrentUserResponse(String email, List<String> roles) {
}
