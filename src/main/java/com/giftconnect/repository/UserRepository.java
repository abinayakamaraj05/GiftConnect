package com.giftconnect.repository;

import com.giftconnect.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Data access layer for the User entity.
 * Extending JpaRepository gives us save(), findAll(), findById(), etc. for free.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Used to check for duplicate emails during registration.
     * Spring Data JPA generates the query automatically from the method name.
     */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
