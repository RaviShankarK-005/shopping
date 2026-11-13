package com.example.shopping.discount;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Validated request for creating a percentage or fixed-amount discount.
 *
 * @param discountType calculation type
 * @param discountValue positive discount amount
 * @param itemId optional product restriction
 * @param validFrom optional start of validity
 * @param validTo optional end of validity
 */
public record DiscountRequest(
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        Long itemId,
        LocalDateTime validFrom,
        LocalDateTime validTo
) {}
