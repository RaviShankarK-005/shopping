package com.example.shopping.item;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Exposes product management and dashboard endpoints.
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {
    private final ItemService service;
    public ItemController(ItemService service) { this.service = service; }

    /**
     * Creates a product from the validated request.
     *
     * @param request product details
     * @return the created product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Item create(@Valid @RequestBody ItemRequest request) { return service.create(request); }

    /** Returns the complete product list. */
    @GetMapping
    public List<Item> getAll() { return service.getAll(); }

    /**
     * Searches product names, ignoring case.
     *
     * @param query text to find in product names
     * @return matching products ordered by name
     */
    @GetMapping("/search")
     public List<Item> search(@RequestParam(name = "query", required = false) String query) {
        return service.search(query);
    }

    /**
     * Gets a product by identifier.
     *
     * @param id product identifier
     * @return the matching product
     */
    @GetMapping("/{id}")
    public Item get(@PathVariable Long id) { return service.get(id); }

    /**
     * Updates a product using the validated request values.
     *
     * @param id product identifier
     * @param request replacement product details
     * @return the updated product
     */
    @PutMapping("/{id}")
    public Item update(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return service.update(id, request);
    }

    /**
     * Deletes a product that has no reserved stock.
     *
     * @param id product identifier
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    /**
     * Returns high-demand products using the requested page window.
     *
     * @param limit page size (defaults to 20)
     * @param offset number of results to skip (defaults to 0)
     * @return matching products
     */
    @GetMapping("/popular")
    public List<Item> getPopularItems(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.getPopularItems(limit, offset);
    }

    /**
     * Returns newest products using the requested page window.
     *
     * @param limit page size (defaults to 20)
     * @param offset number of results to skip (defaults to 0)
     * @return matching products
     */
    @GetMapping("/new")
    public List<Item> getNewItems(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.getNewItems(limit, offset);
    }
}
