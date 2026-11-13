package com.example.shopping.cart;

import com.example.shopping.item.Item;
import com.example.shopping.item.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Applies cart rules and coordinates cart changes with product stock reservations.
 */
@Service
@Transactional
public class CartService {
    private static final int RESERVATION_MINUTES = 15;

    private final CartRepository repository;
    private final ItemRepository itemRepository;
    private final TransactionTemplate tx;

    public CartService(CartRepository repository, ItemRepository itemRepository,
                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Adds units to a cart, reserving stock when the product is high demand.
     *
     * @param userId cart owner identifier
     * @param request product identifier and quantity to add
     * @return the created or updated cart entry
     * @throws IllegalArgumentException if the user, product, or requested quantity is invalid
     */
    public Cart add(Long userId, CartRequest request) {
        validateUser(userId);
        validateQuantity(request.quantity());
        Item item = lockItem(request.itemId());
        Cart existing = repository.findByIdUserIdAndIdItemId(userId, request.itemId()).orElse(null);

        if (existing != null && isExpired(existing)) {
            // Reuse the row: deleting and re-inserting the same key in one transaction can fail with a
            // unique violation, because Hibernate runs inserts before deletes. Hand the held units back
            // and carry on from an empty, unreserved row.
            releaseIfNeeded(item, existing);
            existing.setQuantity(0);
            existing.setReservedUntil(null);
        }

        if (existing != null) {
            boolean alreadyReserved = existing.getReservedUntil() != null;
            int newTotal = existing.getQuantity() + request.quantity();
            if (item.isHighDemand()) {
                item.reserve(request.quantity() + (alreadyReserved ? 0 : existing.getQuantity()));
            } else {
                if (alreadyReserved) item.releaseReservation(existing.getQuantity());
                item.ensureCanHold(newTotal);
            }
            existing.setQuantity(newTotal);
            refreshReservation(existing, item);
            return existing;
        }

        if (item.isHighDemand()) item.reserve(request.quantity());
        else item.ensureCanHold(request.quantity());
        Cart cart = new Cart(userId, request.itemId(), request.quantity());
        refreshReservation(cart, item);
        return repository.save(cart);
    }

    /**
     * Gets the user's cart without opening a write transaction.
     *
     * @param userId cart owner identifier
     * @return cart entries for the user
     */
    @Transactional(readOnly = true)
    public List<Cart> get(Long userId) {
        validateUser(userId);
        return repository.findByIdUserId(userId);
    }
    /**
     * Changes the total quantity of an existing cart entry.
     *
     * @param userId cart owner identifier
     * @param itemId product identifier
     * @param quantity new total quantity
     * @return the updated cart entry
     */
    public Cart update(Long userId, Long itemId, Integer quantity) {
        validateUser(userId);
        validateQuantity(quantity);
        Item item = lockItem(itemId);
        Cart cart = find(userId, itemId);

        if (isExpired(cart)) {
            throw new IllegalArgumentException("The 15-minute reservation expired. Add the item to your cart again.");
        }

        int oldQuantity = cart.getQuantity();
        boolean alreadyReserved = cart.getReservedUntil() != null;
        if (item.isHighDemand()) {
            if (!alreadyReserved) item.reserve(quantity);
            else if (quantity > oldQuantity) item.reserve(quantity - oldQuantity);
            else if (quantity < oldQuantity) item.releaseReservation(oldQuantity - quantity);
        } else {
            if (alreadyReserved) item.releaseReservation(oldQuantity);
            item.ensureCanHold(quantity);
        }
        cart.setQuantity(quantity);
        refreshReservation(cart, item);
        return cart;
    }
    /**
     * Removes a cart entry and releases its reservation if one is active.
     *
     * @param userId cart owner identifier
     * @param itemId product identifier
     */
    public void remove(Long userId, Long itemId) {
        Item item = lockItem(itemId);
        Cart cart = find(userId, itemId);
        releaseIfNeeded(item, cart);
        repository.delete(cart);
    }
    /**
     * Finds expired reservations and processes each row in a short transaction.
     *
     * <p>Uses SUPPORTS propagation so a scheduler call does not hold a long-lived
     * transaction; each expired row is handled in a separate transaction.</p>
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void expireReservations() {
        for (Cart expired : repository.findByReservedUntilBefore(LocalDateTime.now())) {
            CartId id = expired.getId();
            tx.executeWithoutResult(status -> releaseExpired(id));
        }
    }

    private void releaseExpired(CartId id) {
        Item item = itemRepository.findByIdForUpdate(id.getItemId()).orElse(null);   // lock first...
        Cart current = repository.findById(id).orElse(null);                          // ...then re-read the row
        if (current == null || !isExpired(current)) return;
        if (item != null) releaseIfNeeded(item, current);
        repository.delete(current);
    }

    private Item lockItem(Long itemId) {
        return itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item " + itemId + " not found"));
    }

    private Cart find(Long userId, Long itemId) {
        return repository.findByIdUserIdAndIdItemId(userId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));
    }

    /** The 15 minutes start when units are first reserved and are NOT extended by later changes. */
    private void refreshReservation(Cart cart, Item item) {
        if (!item.isHighDemand()) {
            cart.setReservedUntil(null);
        } else if (cart.getReservedUntil() == null) {
            cart.setReservedUntil(LocalDateTime.now().plusMinutes(RESERVATION_MINUTES));
        }
    }

    private boolean isExpired(Cart cart) {
        return cart.getReservedUntil() != null && !cart.getReservedUntil().isAfter(LocalDateTime.now());
    }

    private void releaseIfNeeded(Item item, Cart cart) {
        if (cart.getReservedUntil() != null) {
            item.releaseReservation(cart.getQuantity());
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) throw new IllegalArgumentException("quantity must be at least 1");
    }

    private void validateUser(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("Valid userId is required");
    }
}
