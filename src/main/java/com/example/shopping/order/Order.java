package com.example.shopping.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted order and immutable checkout snapshot.
 */
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "items", nullable = false)
    private String items;

    @Column(name = "discount_code")
    private String discountCode;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Order() {}

    public Order(String orderId, Long userId, String items, String discountCode, BigDecimal total) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = items;
        this.discountCode = discountCode;
        this.total = total;
        this.createdAt = LocalDateTime.now();
    }

    public String getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public String getItems() { return items; }
    public String getDiscountCode() { return discountCode; }
    public BigDecimal getTotal() { return total; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
