package com.example.shopping.item;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Validated product creation or update payload.
 *
 * @param name non-blank product name
 * @param price product price greater than zero
 * @param highDemand whether the product uses timed stock reservations
 * @param stock non-negative total stock
 */
public record ItemRequest(
        @NotBlank String name,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        boolean highDemand,
        @NotNull @Min(0) Integer stock
) {}
