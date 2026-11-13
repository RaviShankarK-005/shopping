package com.example.shopping.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Exposes checkout and order-history endpoints for a user.
 */
@RestController
@RequestMapping("/api/users/{userId}/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    /**
     * Checks out the user's cart and returns 201 for a new order or 200 for a retry.
     *
     * @param userId order owner identifier
     * @param request idempotent checkout request
     * @return the created or previously existing order
     */
    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(@PathVariable Long userId, @Valid @RequestBody CheckoutRequest request) {
        CheckoutResult result = service.checkout(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    /** Returns the user's orders, newest first. */
    @GetMapping
    public List<Order> getOrders(@PathVariable Long userId) { return service.getByUser(userId); }
}
