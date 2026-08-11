package com.giftconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides a single shared PasswordEncoder bean (BCrypt) for hashing and
 * verifying user passwords. This is the ONLY piece of Spring Security we're
 * using — no filter chains, no login pages, no auto-secured endpoints.
 * Authentication itself is handled by our own AuthController + AuthFilter.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
