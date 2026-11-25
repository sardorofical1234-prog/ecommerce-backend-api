package com.sardor.ecommerce.auth;

import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.common.UnauthorizedException;
import com.sardor.ecommerce.security.JwtService;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import com.sardor.ecommerce.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    void registerCreatesCustomerAndReturnsBearerToken() {
        RegisterRequest request = new RegisterRequest("SHOP@EXAMPLE.COM", "strong-password-123", "Test Buyer");
        when(userRepository.existsByEmail("shop@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(jwtService.createAccessToken(any(UserEntity.class))).thenReturn("signed-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request);

        assertEquals("signed-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("shop@example.com", response.user().email());
        assertEquals(UserRole.CUSTOMER.name(), response.user().role());
        verify(passwordEncoder).encode(request.password());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("shop@example.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("shop@example.com", "strong-password-123", "Test Buyer");

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginRejectsIncorrectPassword() {
        UserEntity user = new UserEntity("shop@example.com", "bcrypt-hash", "Test Buyer", UserRole.CUSTOMER);
        when(userRepository.findByEmail("shop@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("wrong-password"), eq("bcrypt-hash"))).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("shop@example.com", "wrong-password")));
    }
}
