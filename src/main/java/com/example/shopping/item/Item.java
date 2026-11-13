package com.example.shopping.item;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product entity containing catalog data and the current stock balance.
 */
@Entity
@Table(name = "item", indexes = {
        @Index(name = "idx_item_high_demand_listed", columnList = "high_demand, listed_at"),
        @Index(name = "idx_item_listed_at", columnList = "listed_at")
})
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private BigDecimal price;
    @Column(name = "listed_at")
    private LocalDateTime listedAt;
    @Column(name = "high_demand")
    private boolean highDemand;
    private Integer stock;
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reserved = 0;

    protected Item() {}

    /**
     * Creates a new product with no reserved stock.
     *
     * @throws IllegalArgumentException if stock is negative or missing
     */
    public Item(String name, BigDecimal price, boolean highDemand, Integer stock) {
        if (stock == null || stock < 0) throw new IllegalArgumentException("stock cannot be negative");
        this.name = name;
        this.price = price;
        this.listedAt = LocalDateTime.now();
        this.highDemand = highDemand;
        this.stock = stock;
        this.reserved = 0;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getListedAt() { return listedAt; }
    public boolean isHighDemand() { return highDemand; }
    public Integer getStock() { return stock; }
    public Integer getReserved() { return reserved; }
    public Integer getAvailableStock() { return stock - reserved; }

    /** For items without a timed reservation: the bag may never hold more than the unreserved stock. */
    public void ensureCanHold(int totalQuantity) {
        if (totalQuantity < 1 || totalQuantity > getAvailableStock()) {
            throw new IllegalArgumentException("Only " + getAvailableStock() + " available for item " + id);
        }
    }

    /**
     * Changes product data without allowing reserved stock to be invalidated.
     */
    public void update(String name, BigDecimal price, boolean highDemand, Integer stock) {
        if (stock == null || stock < reserved) {
            throw new IllegalArgumentException("stock cannot be lower than currently reserved stock (" + reserved + ")");
        }
        if (this.highDemand != highDemand && reserved > 0) {
            throw new IllegalArgumentException("highDemand cannot be changed while stock is reserved");
        }
        this.name = name;
        this.price = price;
        this.highDemand = highDemand;
        this.stock = stock;
    }

    /**
     * Reserves available units for a high-demand cart entry.
     *
     * @throws IllegalArgumentException if the quantity is invalid or unavailable
     */
    public void reserve(int quantity) {
        if (quantity < 1 || getAvailableStock() < quantity) {
            throw new IllegalArgumentException("Not enough stock available for item " + id);
        }
        reserved += quantity;
    }

    /** Releases up to the supplied number of previously reserved units. */
    public void releaseReservation(int quantity) {
        reserved = Math.max(0, reserved - quantity);
    }

    /**
     * Completes a reservation by reducing both reserved and total stock.
     *
     * @throws IllegalArgumentException if the reservation cannot be fulfilled
     */
    public void completeReservation(int quantity) {
        if (quantity < 1 || reserved < quantity || stock < quantity) {
            throw new IllegalArgumentException("Reservation is no longer available for item " + id);
        }
        reserved -= quantity;
        stock -= quantity;
    }

    /**
     * Purchases units that were not previously reserved.
     *
     * @throws IllegalArgumentException if the requested stock is unavailable
     */
    public void purchaseAvailable(int quantity) {
        if (quantity < 1 || getAvailableStock() < quantity) {
            throw new IllegalArgumentException("Not enough stock available for item " + id);
        }
        stock -= quantity;
    }
}
