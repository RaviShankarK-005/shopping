package com.example.shopping.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the location interceptor for all API routes.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LocationInterceptor locationInterceptor;

    public WebConfig(LocationInterceptor locationInterceptor) {
        this.locationInterceptor = locationInterceptor;
    }

    /** Adds the location restriction to requests under {@code /api/**}. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(locationInterceptor)
                .addPathPatterns("/api/**"); // Automatically enforces London restriction across all API routes
    }
}