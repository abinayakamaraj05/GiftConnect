package com.giftconnect.service;

import com.giftconnect.entity.User;
import com.giftconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Business logic for user registration and lookup.
 * Controllers should never talk to the repository directly — they go through this service.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user after checking that the email isn't already taken.
     * Throws IllegalStateException if the email is a duplicate — the controller
     * catches this and returns HTTP 409 Conflict.
     */
    public User registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalStateException("Email already registered: " + user.getEmail());
        }
        return userRepository.save(user);
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
