package com.example.shopping.cart;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A user's cart entry, keyed by the user and product identifiers.
 */
@Entity
@Table(name = "cart")
public class Cart {
    @EmbeddedId
    private CartId id;
    private Integer quantity;
    @Column(name = "added_at")
    private LocalDateTime addedAt;
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

    protected Cart() {}
    public Cart(Long userId, Long itemId, Integer quantity) {
        this.id = new CartId(userId, itemId);
        this.quantity = quantity;
        this.addedAt = LocalDateTime.now();
        this.reservedUntil = null;
    }
    public CartId getId() { return id; }
    public Integer getQuantity() { return quantity; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public LocalDateTime getReservedUntil() { return reservedUntil; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setReservedUntil(LocalDateTime reservedUntil) { this.reservedUntil = reservedUntil; }
}
