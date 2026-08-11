package com.giftconnect.service;

import com.giftconnect.entity.User;
import com.giftconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Business logic for user registration, login, and lookup.
 * Controllers should never talk to the repository directly — they go through this service.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user after checking that the email isn't already taken.
     * The plaintext password is hashed with BCrypt before it's ever saved —
     * the raw password is never persisted or logged.
     * Throws IllegalStateException if the email is a duplicate — the controller
     * catches this and returns HTTP 409 Conflict.
     */
    public User registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalStateException("Email already registered: " + user.getEmail());
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    /**
     * Verifies login credentials. Returns the matching User if the email exists
     * AND the given plaintext password matches the stored hash. Returns
     * Optional.empty() for either a missing user or a wrong password — the
     * controller treats both cases identically (401) so we don't leak which
     * one was wrong.
     */
    public Optional<User> authenticate(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Throws NoSuchElementException if no user exists with the given id —
     * the controller catches this and returns HTTP 404 Not Found.
     */
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
    }
}
