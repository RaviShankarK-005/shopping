package com.example.shopping.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Checkout input using a client-generated idempotency/order identifier.
 *
 * @param orderId non-blank stable identifier reused for retries, up to 100 characters
 * @param discountCode optional discount code
 */
public record CheckoutRequest(
        @NotBlank(message = "orderId is required and must be reused when retrying checkout")
        @Size(max = 100, message = "orderId cannot exceed 100 characters") String orderId,
        String discountCode) {}
