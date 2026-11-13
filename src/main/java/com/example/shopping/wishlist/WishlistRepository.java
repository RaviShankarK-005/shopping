package com.example.shopping.wishlist;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** Persistence queries for user wishlist entries. */
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    /** Lists a user's wishlist entries. */
    List<Wishlist> findByUserId(Long userId);

    /** Finds the entry for a specific user and product. */
    Optional<Wishlist> findByUserIdAndItemId(Long userId, Long itemId);
}
