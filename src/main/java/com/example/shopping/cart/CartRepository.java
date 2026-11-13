package com.example.shopping.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for cart entries and reservation expiry queries.
 */
public interface CartRepository extends JpaRepository<Cart, CartId> {
    /** Finds the cart entries for a user. */
    List<Cart> findByIdUserId(Long userId);

    /** Finds one entry by its user and product identifiers. */
    Optional<Cart> findByIdUserIdAndIdItemId(Long userId, Long itemId);

    /** Finds entries whose reservation deadline is before the supplied time. */
    List<Cart> findByReservedUntilBefore(LocalDateTime time);

    /** Ids only, so no Cart entities are cached before the item locks are taken. */
    @Query("select c.id.itemId from Cart c where c.id.userId = :userId")
    List<Long> findItemIdsByUserId(@Param("userId") Long userId);
}
