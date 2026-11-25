package com.sardor.ecommerce.auth;

import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.common.UnauthorizedException;
import com.sardor.ecommerce.security.JwtService;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import com.sardor.ecommerce.user.UserRole;
import com.sardor.ecommerce.user.UserSummary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        UserEntity user = new UserEntity(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                UserRole.CUSTOMER
        );
        UserEntity saved = userRepository.save(user);
        return tokenResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return tokenResponse(user);
    }

    private AuthResponse tokenResponse(UserEntity user) {
        return new AuthResponse(jwtService.createAccessToken(user), "Bearer",
                jwtService.accessTokenTtlSeconds(), UserSummary.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
