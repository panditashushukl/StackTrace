package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.dtos.request.LoginRequest;
import com.snsgos.stacktrace.dtos.request.RegisterRequest;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.enums.Role;
import com.snsgos.stacktrace.exception.ApiException;
import com.snsgos.stacktrace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void processOAuthPostLogin_shouldCreateNewUserWhenNotFound() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-12345");
        attributes.put("email", "john.doe@example.com");
        attributes.put("name", "John Doe");
        attributes.put("picture", "https://avatar.example.com/john.png");

        when(userRepository.findByGoogleId("google-12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        User user = userService.processOAuthPostLogin(attributes);

        assertNotNull(user);
        assertEquals("google-12345", user.getGoogleId());
        assertEquals("john.doe@example.com", user.getEmail());
        assertEquals("John Doe", user.getName());
        assertEquals(Role.ROLE_USER, user.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void processOAuthPostLogin_shouldUpdateExistingUser() {
        UUID existingId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(existingId)
                .googleId("google-12345")
                .email("john.doe@example.com")
                .name("Old Name")
                .username("johndoe")
                .role(Role.ROLE_USER)
                .build();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-12345");
        attributes.put("email", "john.doe@example.com");
        attributes.put("name", "New Name");
        attributes.put("picture", "https://avatar.example.com/new.png");

        when(userRepository.findByGoogleId("google-12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User result = userService.processOAuthPostLogin(attributes);

        assertEquals("New Name", result.getName());
        assertEquals("https://avatar.example.com/new.png", result.getAvatarUrl());
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    void registerAndAuthenticate_shouldWorkWithUsernameOrEmail() {
        RegisterRequest registerRequest = new RegisterRequest("Jane Dev", "janedev", "Jane@Example.com", "strongPass123");
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("janedev")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User created = userService.register(registerRequest);
        assertNull(created.getGoogleId());
        assertNotNull(created.getPasswordHash());
        assertTrue(passwordEncoder.matches("strongPass123", created.getPasswordHash()));

        when(userRepository.findByEmailIgnoreCase("janedev")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("janedev")).thenReturn(Optional.of(created));

        User authenticated = userService.authenticate(new LoginRequest("janedev", "strongPass123"));
        assertEquals(created.getId(), authenticated.getId());

        assertThrows(ApiException.class, () ->
                userService.authenticate(new LoginRequest("janedev", "wrongPassword")));
    }

    @Test
    void updatePassword_shouldUpdateWithoutRequiringOldPassword() {
        UUID userId = UUID.randomUUID();
        User googleUser = User.builder()
                .id(userId)
                .googleId("g-99")
                .email("oauth@example.com")
                .name("OAuth User")
                .username("oauthuser")
                .role(Role.ROLE_USER)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(googleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updatePassword(userId, "newSecurePassword99");

        assertNotNull(googleUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("newSecurePassword99", googleUser.getPasswordHash()));
    }
}
