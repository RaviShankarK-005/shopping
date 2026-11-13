package com.example.shopping.discount;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Discount terms, optional product scope, and optional validity window.
 */
@Entity
@Table(name = "discount")
public class Discount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String code;
    @Column(name = "discount_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;
    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;
    @Column(name = "item_id")
    private Long itemId;
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    @Column(name = "valid_to")
    private LocalDateTime validTo;

    protected Discount() {}
    public Discount(String code, DiscountType type, BigDecimal value, Long itemId,
                    LocalDateTime validFrom, LocalDateTime validTo) {
        this.code = code; this.discountType = type; this.discountValue = value;
        this.itemId = itemId; this.validFrom = validFrom; this.validTo = validTo;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public Long getItemId() { return itemId; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidTo() { return validTo; }
    /** Checks whether the discount is valid at the current server time. */
    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return (validFrom == null || !now.isBefore(validFrom))
                && (validTo == null || !now.isAfter(validTo));
    }
}
