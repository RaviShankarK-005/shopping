package com.example.shopping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bootstraps the shopping application and enables scheduled reservation cleanup.
 */
@SpringBootApplication
@EnableScheduling
public class ShoppingApplication {
    /**
     * Starts the Spring application using the supplied command-line arguments.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ShoppingApplication.class, args);
    }
}
