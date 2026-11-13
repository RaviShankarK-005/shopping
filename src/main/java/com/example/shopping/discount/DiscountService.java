package com.example.shopping.discount;

import com.example.shopping.item.ItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Validates discount definitions and provides discount lookup operations.
 */
@Service
@Transactional
public class DiscountService {
    private final DiscountRepository repository;
    private final ItemService itemService;
    public DiscountService(DiscountRepository repository, ItemService itemService) {
        this.repository = repository; this.itemService = itemService;
    }
    /**
     * Creates a discount after validating its dates, amount, and optional product.
     *
     * @param request discount terms
     * @return the persisted discount with its generated code
     */
    public Discount create(DiscountRequest request) {
        if (request.validFrom() != null && request.validTo() != null
                && request.validTo().isBefore(request.validFrom()))
            throw new IllegalArgumentException("validTo must be after validFrom");
        if (request.discountType() == DiscountType.PERCENT && request.discountValue().compareTo(new java.math.BigDecimal("100")) > 0)
            throw new IllegalArgumentException("A percentage discount cannot be more than 100");
        if (request.itemId() != null) itemService.get(request.itemId());
        // Generate a short, readable code on the server. The database unique
        // constraint remains the final protection against a rare collision.
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase();
        } while (repository.findByCodeIgnoreCase(code).isPresent());

        return repository.save(new Discount(code, request.discountType(),
                request.discountValue(), request.itemId(), request.validFrom(), request.validTo()));
    }
    /** Returns every persisted discount. */
    @Transactional(readOnly = true)
    public List<Discount> getAll() { return repository.findAll(); }
    /** Returns discounts whose optional validity bounds include the current time. */
    @Transactional(readOnly = true)
    public List<Discount> getAvailable() {
        return repository.findAvailableAt(LocalDateTime.now());
    }
    /**
     * Finds a discount by code, ignoring letter case.
     *
     * @throws NoSuchElementException if no matching code exists
     */
    @Transactional(readOnly = true)
    public Discount getByCode(String code) {
        return repository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NoSuchElementException("Discount code not found"));
    }
}
