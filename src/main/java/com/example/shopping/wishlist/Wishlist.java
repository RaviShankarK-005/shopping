package com.example.shopping.wishlist;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A saved product association for a user.
 */
@Entity
@Table(name = "wishlist", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "item_id"}))
public class Wishlist {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "item_id", nullable = false)
    private Long itemId;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected Wishlist() {}
    public Wishlist(Long userId, Long itemId) {
        this.userId = userId; this.itemId = itemId; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getItemId() { return itemId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
