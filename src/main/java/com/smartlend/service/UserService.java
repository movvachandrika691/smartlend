package com.smartlend.service;

import com.smartlend.exception.BadRequestException;
import com.smartlend.exception.ResourceNotFoundException;
import com.smartlend.model.entity.User;
import com.smartlend.model.enums.Role;
import com.smartlend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for User entity business logic.
 * Handles user CRUD operations and role management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new user with encrypted password.
     */
    @Transactional
    public User registerUser(String name, String email, String password, Role role) {
        log.info("Registering new user with email: {}", email);

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already registered: " + email);
        }

        // Create user with encoded password
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        return savedUser;
    }

    /**
     * Find user by email for authentication.
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    /**
     * Find user by ID.
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Get all users by role.
     */
    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    /**
     * Count users by role - for admin statistics.
     */
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    /**
     * Validate user exists - used for authorization checks.
     */
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }
}
