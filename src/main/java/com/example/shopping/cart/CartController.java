package com.example.shopping.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Exposes operations for adding, viewing, changing, and removing cart entries.
 */
@RestController
@Validated
@RequestMapping("/api/users/{userId}/cart")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }

    /**
     * Adds the requested quantity to the user's cart.
     *
     * @param userId cart owner identifier
     * @param request product identifier and quantity to add
     * @return the created or updated cart entry
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Cart add(@PathVariable Long userId, @Valid @RequestBody CartRequest request) {
        return service.add(userId, request);
    }

    /**
     * Returns all cart entries belonging to the user.
     *
     * @param userId cart owner identifier
     * @return the user's cart entries
     */
    @GetMapping
    public List<Cart> get(@PathVariable Long userId) { return service.get(userId); }

    /**
     * Sets the quantity for an existing cart entry.
     *
     * @param userId cart owner identifier
     * @param itemId product identifier
     * @param quantity requested total quantity
     * @return the updated cart entry
     */
    @PutMapping("/items/{itemId}")
    public Cart update(@PathVariable Long userId, @PathVariable Long itemId,
                       @RequestParam @Min(1) Integer quantity) {
        return service.update(userId, itemId, quantity);
    }

    /**
     * Removes a cart entry and releases any associated stock reservation.
     *
     * @param userId cart owner identifier
     * @param itemId product identifier
     */
    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long userId, @PathVariable Long itemId) {
        service.remove(userId, itemId);
    }
}
