package com.example.shopping.wishlist;

import com.example.shopping.item.ItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages wishlist entries and validates referenced products.
 */
@Service
@Transactional
public class WishlistService {
    private final WishlistRepository repository;
    private final ItemService itemService;

    public WishlistService(WishlistRepository repository, ItemService itemService) {
        this.repository = repository; this.itemService = itemService;
    }

    /**
     * Adds a product to a user's wishlist.
     *
     * @throws IllegalArgumentException if the user is invalid or the product is already liked
     */
    public Wishlist add(Long userId, Long itemId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("Valid userId is required");
        itemService.get(itemId);
        if (repository.findByUserIdAndItemId(userId, itemId).isPresent())
            throw new IllegalArgumentException("Item is already in wishlist");
        return repository.save(new Wishlist(userId, itemId));
    }

    /** Returns the wishlist entries belonging to a user. */
    @Transactional(readOnly = true)
    public List<Wishlist> get(Long userId) { return repository.findByUserId(userId); }

    /** Removes a product from a user's wishlist. */
    public void remove(Long userId, Long itemId) {
        Wishlist entry = repository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Wishlist entry not found"));
        repository.delete(entry);
    }
}
