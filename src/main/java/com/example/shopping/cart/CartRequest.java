package com.example.shopping.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Validated request payload for adding a product to a cart.
 *
 * @param itemId identifier of the product to add
 * @param quantity positive quantity to add
 */
public record CartRequest(@NotNull Long itemId, @NotNull @Min(1) Integer quantity) {}
