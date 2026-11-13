package com.example.shopping.discount;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Exposes discount creation, lookup, and availability endpoints.
 */
@RestController
@RequestMapping("/api/discounts")
public class DiscountController {
    private final DiscountService service;
    public DiscountController(DiscountService service) { this.service = service; }

    /** Creates a discount and returns its generated code. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Discount create(@Valid @RequestBody DiscountRequest request) { return service.create(request); }

    /** Returns all discounts, whether active or expired. */
    @GetMapping
    public List<Discount> getAll() { return service.getAll(); }

    /** Returns discounts valid at the current server time. */
    @GetMapping("/available")
    public List<Discount> getAvailable() { return service.getAvailable(); }

    /** Looks up a discount without regard to code letter case. */
    @GetMapping("/{code}")
    public Discount getByCode(@PathVariable String code) { return service.getByCode(code); }
}
