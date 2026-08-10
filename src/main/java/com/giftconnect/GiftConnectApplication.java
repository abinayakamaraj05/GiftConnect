package com.giftconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the GiftConnect Spring Boot application.
 * Running this class starts the embedded web server (default port 8080).
 */
@SpringBootApplication
public class GiftConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(GiftConnectApplication.class, args);
    }

}
