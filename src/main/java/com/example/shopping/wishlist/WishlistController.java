package com.example.shopping.wishlist;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Exposes a user's product wishlist operations.
 */
@RestController
@RequestMapping("/api/users/{userId}/wishlist")
public class WishlistController {
    private final WishlistService service;
    public WishlistController(WishlistService service) { this.service = service; }

    /** Adds a product to the user's wishlist. */
    @PostMapping("/{itemId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Wishlist add(@PathVariable Long userId, @PathVariable Long itemId) {
        return service.add(userId, itemId);
    }

    /** Returns the user's wishlist entries. */
    @GetMapping
    public List<Wishlist> get(@PathVariable Long userId) { return service.get(userId); }

    /** Removes a product from the user's wishlist. */
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long userId, @PathVariable Long itemId) {
        service.remove(userId, itemId);
    }
}
