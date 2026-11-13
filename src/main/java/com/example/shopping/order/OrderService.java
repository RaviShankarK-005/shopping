package com.example.shopping.order;

import com.example.shopping.cart.Cart;
import com.example.shopping.cart.CartRepository;
import com.example.shopping.discount.*;
import com.example.shopping.item.Item;
import com.example.shopping.item.ItemRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Checks out carts transactionally and applies stock, discount, and idempotency rules.
 */
@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ItemRepository itemRepository;
    private final DiscountService discountService;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, CartRepository cartRepository,
                        ItemRepository itemRepository, DiscountService discountService,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.discountService = discountService;
        this.objectMapper = objectMapper;
    }
    /**
     * Places an order from the user's current cart.
     *
     * @param userId order owner identifier
     * @param request client-generated order ID and optional discount code
     * @return checkout result indicating whether a new order was created
     * @throws IllegalArgumentException if checkout inputs, cart contents, stock, or discount are invalid
     */
    public CheckoutResult checkout(Long userId, CheckoutRequest request) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("Valid userId is required");
        if (request == null || request.orderId() == null || request.orderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required. Reuse the same value if checkout is retried.");
        }

        String orderId = request.orderId().trim();
        Order previousOrder = orderRepository.findById(orderId).orElse(null);
        if (previousOrder != null) return existingOrder(previousOrder, userId);

        // Read only the item ids first (a projection, so no Cart entities get cached in this transaction),
        // lock those items in a stable order, and only then load the cart rows. Loading the rows before the
        // lock would hand back stale cached instances if the cart changed while we waited.
        List<Long> itemIds = cartRepository.findItemIdsByUserId(userId).stream().sorted().toList();
        if (itemIds.isEmpty()) throw new IllegalArgumentException("Cart is empty");

        Map<Long, Item> lockedItems = new LinkedHashMap<>();
        for (Long itemId : itemIds) {
            lockedItems.put(itemId, itemRepository.findByIdForUpdate(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item " + itemId + " not found")));
        }

        // Another request using this orderId may have completed while this request waited for a lock.
        previousOrder = orderRepository.findById(orderId).orElse(null);
        if (previousOrder != null) return existingOrder(previousOrder, userId);

        List<Cart> cartItems = cartRepository.findByIdUserId(userId).stream()
                .sorted(Comparator.comparing(cart -> cart.getId().getItemId())).toList();
        if (cartItems.isEmpty()) throw new IllegalArgumentException("Cart is empty");
        for (Cart cart : cartItems) {
            if (!lockedItems.containsKey(cart.getId().getItemId())) {
                throw new IllegalArgumentException("Cart changed during checkout. Please retry with the same orderId.");
            }
        }

        Discount discount = null;
        if (request.discountCode() != null && !request.discountCode().isBlank()) {
            discount = discountService.getByCode(request.discountCode());
            if (!discount.isCurrentlyValid()) throw new IllegalArgumentException("Discount is not currently valid");
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal fixedDiscountEligibleTotal = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();

        for (Cart cart : cartItems) {
            Item item = lockedItems.get(cart.getId().getItemId());
            if (item.isHighDemand()) {
                if (cart.getReservedUntil() == null || !cart.getReservedUntil().isAfter(now)) {
                    throw new IllegalArgumentException("Reservation expired for item " + item.getId() + ". Add it to your cart again.");
                }
                item.completeReservation(cart.getQuantity());
            } else {
                item.purchaseAvailable(cart.getQuantity());
            }

            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity()));
            if (discount != null && (discount.getItemId() == null || discount.getItemId().equals(item.getId()))) {
                if (discount.getDiscountType() == DiscountType.PERCENT) {
                    lineTotal = lineTotal.multiply(BigDecimal.ONE.subtract(
                            discount.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
                } else {
                    fixedDiscountEligibleTotal = fixedDiscountEligibleTotal.add(lineTotal);
                }
            }
            total = total.add(lineTotal);
            snapshots.add(Map.of("itemId", item.getId(), "name", item.getName(),
                    "qty", cart.getQuantity(), "unitPrice", item.getPrice()));
        }

        if (discount != null && discount.getDiscountType() == DiscountType.FIXED
                && fixedDiscountEligibleTotal.compareTo(BigDecimal.ZERO) > 0) {
            // A fixed amount can never take off more than the eligible items cost.
            total = total.subtract(discount.getDiscountValue().min(fixedDiscountEligibleTotal));
        }
        total = total.setScale(2, RoundingMode.HALF_UP);   // same value in the response and in the database

        try {
            String itemsJson = objectMapper.writeValueAsString(snapshots);
            Order order = orderRepository.save(new Order(orderId, userId, itemsJson,
                    discount == null ? null : discount.getCode(), total));
            cartRepository.deleteAll(cartItems);
            return new CheckoutResult(order, true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize order items", e);
        }
    }

    private CheckoutResult existingOrder(Order order, Long userId) {
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("This orderId has already been used");
        }
        return new CheckoutResult(order, false);
    }

    /** Returns the user's orders from newest to oldest. */
    @Transactional(readOnly = true)
    public List<Order> getByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
