package com.example.shopping.item;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Implements product operations and enforces product stock constraints.
 */
@Service
@Transactional
public class ItemService {
    private final ItemRepository repository;

    public ItemService(ItemRepository repository) { this.repository = repository; }
    /** Creates and persists a product. */
    public Item create(ItemRequest request) {
        return repository.save(new Item(request.name(), request.price(),
                request.highDemand(), request.stock()));
    }

    /** Returns all persisted products. */
    @Transactional(readOnly = true)
    public List<Item> getAll() { return repository.findAll(); }

    /**
     * Searches product names using a case-insensitive substring match.
     *
     * @param query text to find in product names
     * @return matching products ordered by name, or an empty list for a blank query
     */
    @Transactional(readOnly = true)
    public List<Item> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
    }

    /**
     * Finds a product by identifier.
     *
     * @throws NoSuchElementException if the product does not exist
     */
    @Transactional(readOnly = true)
    public Item get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Item " + id + " not found"));
    }
    /**
     * Updates a product while locking its row against concurrent stock changes.
     *
     * @throws NoSuchElementException if the product does not exist
     */
    public Item update(Long id, ItemRequest request) {
        Item item = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Item " + id + " not found"));
        item.update(request.name(), request.price(), request.highDemand(), request.stock());
        return item;
    }
    /**
     * Deletes a product only when it has no outstanding reservations.
     *
     * @throws NoSuchElementException if the product does not exist
     * @throws IllegalArgumentException if reserved stock remains
     */
    public void delete(Long id) {
        Item item = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Item " + id + " not found"));
        if (item.getReserved() > 0) throw new IllegalArgumentException("Cannot delete an item while stock is reserved");
        repository.delete(item);
    }

    /**
     * Lists high-demand products for a page window.
     */
    @Transactional(readOnly = true)
    public List<Item> getPopularItems(int limit, int offset) {
        int page = offset / Math.max(1, limit);
        Pageable pageable = PageRequest.of(page, limit);
        return repository.findPopularItems(pageable);
    }

    /**
     * Lists newest products for a page window.
     */
    @Transactional(readOnly = true)
    public List<Item> getNewItems(int limit, int offset) {
        int page = offset / Math.max(1, limit);
        PageRequest pageable = PageRequest.of(page, limit);
        return repository.findNewItems(pageable);
    }
}
