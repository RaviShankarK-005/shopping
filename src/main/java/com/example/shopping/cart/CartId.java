package com.example.shopping.cart;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite database identifier for a cart entry.
 */
@Embeddable
public class CartId implements Serializable {
    private Long userId;
    private Long itemId;

    protected CartId() {}
    public CartId(Long userId, Long itemId) { this.userId = userId; this.itemId = itemId; }
    public Long getUserId() { return userId; }
    public Long getItemId() { return itemId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartId cartId)) return false;
        return Objects.equals(userId, cartId.userId) && Objects.equals(itemId, cartId.itemId);
    }
    @Override public int hashCode() { return Objects.hash(userId, itemId); }
}
