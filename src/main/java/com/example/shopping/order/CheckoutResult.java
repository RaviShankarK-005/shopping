package com.example.shopping.order;

/**
 * Outcome of checkout, including whether this call created the order.
 *
 * @param order persisted order
 * @param created true for a new order, false when returning an idempotent retry
 */
public record CheckoutResult(Order order, boolean created) {}
