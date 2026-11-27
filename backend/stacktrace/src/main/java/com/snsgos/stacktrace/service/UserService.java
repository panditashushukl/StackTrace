package com.snsgos.stacktrace.service;

import com.snsgos.stacktrace.dtos.request.LoginRequest;
import com.snsgos.stacktrace.dtos.request.RegisterRequest;
import com.snsgos.stacktrace.dtos.response.UserProfileResponse;
import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.enums.Role;
import com.snsgos.stacktrace.exception.ApiException;
import com.snsgos.stacktrace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User processOAuthPostLogin(Map<String, Object> attributes) {
        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");

        if (email == null || googleId == null) {
            throw new IllegalArgumentException("Google OAuth response missing essential claims (sub, email)");
        }

        Optional<User> existingUserOpt = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email));

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            boolean updated = false;

            if (existingUser.getGoogleId() == null || !existingUser.getGoogleId().equals(googleId)) {
                existingUser.setGoogleId(googleId);
                updated = true;
            }
            if (name != null && !name.isBlank() && !name.equals(existingUser.getName())) {
                existingUser.setName(name);
                updated = true;
            }
            if (picture != null && !picture.equals(existingUser.getAvatarUrl())) {
                existingUser.setAvatarUrl(picture);
                updated = true;
            }

            return updated ? userRepository.save(existingUser) : existingUser;
        }

        String username = generateUniqueUsername(email, name);

        User newUser = User.builder()
                .googleId(googleId)
                .name(name != null && !name.isBlank() ? name : username)
                .username(username)
                .email(email)
                .avatarUrl(picture)
                .role(Role.ROLE_USER)
                .build();

        log.info("Registering new user via Google OAuth: email={}, username={}", email, username);
        return userRepository.save(newUser);
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim();
        String name = request.name().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "This username is already taken");
        }

        User user = User.builder()
                .name(name)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .build();

        log.info("Registering new user with credentials: username={}", username);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        String identifier = request.identifier().trim();
        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
        }

        return user;
    }

    @Transactional
    public void updatePassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Updated password for user id={}", userId);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID userId) {
        User user = getUserEntity(userId);
        return toProfileResponse(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntity(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));
    }

    public UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank(),
                user.getGoogleId() != null && !user.getGoogleId().isBlank()
        );
    }

    private String generateUniqueUsername(String email, String name) {
        String base = (email != null && email.contains("@"))
                ? email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "")
                : (name != null ? name.replaceAll("\\s+", "").toLowerCase() : "user");

        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        if (base.isBlank()) {
            base = "user";
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }
}
