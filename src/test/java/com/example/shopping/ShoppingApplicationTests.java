package com.example.shopping;

import com.example.shopping.cart.Cart;
import com.example.shopping.cart.CartRepository;
import com.example.shopping.cart.CartRequest;
import com.example.shopping.cart.CartService;
import com.example.shopping.discount.DiscountRequest;
import com.example.shopping.discount.DiscountService;
import com.example.shopping.discount.DiscountType;
import com.example.shopping.item.Item;
import com.example.shopping.item.ItemRepository;
import com.example.shopping.item.ItemService;
import com.example.shopping.order.CheckoutRequest;
import com.example.shopping.order.CheckoutResult;
import com.example.shopping.order.OrderService;
import com.example.shopping.wishlist.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShoppingApplicationTests {

    @Autowired private DiscountService discountService;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ItemService itemService;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private WishlistService wishlistService;
    @Autowired private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void discountCodeIsGeneratedAutomatically() {
        var discount = discountService.create(new DiscountRequest(
                DiscountType.PERCENT, new BigDecimal("10"), null, null, null));

        assertNotNull(discount.getCode());
        assertTrue(Pattern.matches("[A-F0-9]{12}", discount.getCode()));
    }

    @Test
    void onlyCurrentlyValidDiscountsAreReturnedAndAcceptedAtCheckout() {
        LocalDateTime now = LocalDateTime.now();
        var active = discountService.create(new DiscountRequest(
                DiscountType.PERCENT, new BigDecimal("10"), null, now.minusHours(1), now.plusHours(1)));
        var expired = discountService.create(new DiscountRequest(
                DiscountType.PERCENT, new BigDecimal("20"), null, now.minusDays(2), now.minusDays(1)));
        var future = discountService.create(new DiscountRequest(
                DiscountType.PERCENT, new BigDecimal("30"), null, now.plusDays(1), now.plusDays(2)));

        var availableCodes = discountService.getAvailable().stream().map(d -> d.getCode()).toList();
        assertTrue(availableCodes.contains(active.getCode()));
        assertFalse(availableCodes.contains(expired.getCode()));
        assertFalse(availableCodes.contains(future.getCode()));

        Item item = itemRepository.save(new Item("Watch", new BigDecimal("100.00"), false, 5));
        cartService.add(501L, new CartRequest(item.getId(), 1));

        assertThrows(IllegalArgumentException.class, () ->
                orderService.checkout(501L, new CheckoutRequest("ORD-EXP-501", expired.getCode())));
        assertThrows(IllegalArgumentException.class, () ->
                orderService.checkout(501L, new CheckoutRequest("ORD-FUT-501", future.getCode())));
    }

    @Test
    void wishlistAddGetPreventDuplicateAndRemove() {
        Item item = itemRepository.save(new Item("Liked Headphones", new BigDecimal("59.99"), false, 10));

        wishlistService.add(601L, item.getId());
        assertEquals(1, wishlistService.get(601L).size());
        assertEquals(item.getId(), wishlistService.get(601L).get(0).getItemId());

        // Duplicate like must be rejected
        assertThrows(IllegalArgumentException.class, () -> wishlistService.add(601L, item.getId()));

        wishlistService.remove(601L, item.getId());
        assertTrue(wishlistService.get(601L).isEmpty());
    }

    @Test
    void addingHighDemandItemReservesStockForFifteenMinutes() {
        Item item = itemRepository.save(new Item("Popular item", new BigDecimal("25.00"), true, 5));

        Cart cart = cartService.add(99L, new CartRequest(item.getId(), 2));

        Item updatedItem = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(2, updatedItem.getReserved());
        assertEquals(3, updatedItem.getAvailableStock());
        assertTrue(cart.getReservedUntil().isAfter(LocalDateTime.now().plusMinutes(14)));
    }

    @Test
    void reservationExpiryReleasesStockAndRemovesCartEntry() {
        Item item = itemRepository.save(new Item("Popular item", new BigDecimal("25.00"), true, 5));
        Cart cart = cartService.add(99L, new CartRequest(item.getId(), 2));
        cart.setReservedUntil(LocalDateTime.now().minusMinutes(1));

        cartService.expireReservations();

        Item updatedItem = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(0, updatedItem.getReserved());
        assertTrue(cartRepository.findByIdUserId(99L).isEmpty());
    }

    @Test
    void checkoutUsesOrderIdAsIdempotencyKeyAndPreventsCrossUserReuse() {
        Item item = itemRepository.save(new Item("Test item", new BigDecimal("25.00"), false, 10));
        cartService.add(99L, new CartRequest(item.getId(), 2));

        CheckoutRequest request = new CheckoutRequest("ORDER-USER-99-001", null);
        CheckoutResult first = orderService.checkout(99L, request);
        CheckoutResult retry = orderService.checkout(99L, request);

        assertTrue(first.created());
        assertFalse(retry.created());
        assertEquals(first.order().getOrderId(), retry.order().getOrderId());
        assertEquals(new BigDecimal("50.00"), first.order().getTotal());
        assertEquals(8, itemRepository.findById(item.getId()).orElseThrow().getStock());
        assertTrue(cartRepository.findByIdUserId(99L).isEmpty());

        // Another user cannot hijack the same orderId
        assertThrows(IllegalArgumentException.class, () -> orderService.checkout(100L, request));
    }

    @Test
    void twoUsersCannotReserveTheSameLastUnit() {
        Item item = itemRepository.save(new Item("Last popular item", new BigDecimal("25.00"), true, 1));

        cartService.add(201L, new CartRequest(item.getId(), 1));

        assertThrows(IllegalArgumentException.class,
                () -> cartService.add(202L, new CartRequest(item.getId(), 1)));
        assertEquals(1, itemRepository.findById(item.getId()).orElseThrow().getReserved());
    }

    @Test
    void checkoutConsumesHighDemandReservation() {
        Item item = itemRepository.save(new Item("Popular item", new BigDecimal("25.00"), true, 5));
        cartService.add(100L, new CartRequest(item.getId(), 2));

        orderService.checkout(100L, new CheckoutRequest("ORDER-USER-100-001", null));

        Item updatedItem = itemRepository.findById(item.getId()).orElseThrow();
        assertEquals(3, updatedItem.getStock());
        assertEquals(0, updatedItem.getReserved());
    }

    @Test
    void popularAndNewDashboardsReturnAtMostTwentyItemsByDefault() {
        for (int i = 0; i < 25; i++) {
            itemRepository.save(new Item("Item " + i, new BigDecimal("10.00"), true, 5));
        }

        assertEquals(20, itemRepository.findPopularItems(org.springframework.data.domain.PageRequest.of(0, 20)).size());
        assertEquals(20, itemRepository.findNewItems(org.springframework.data.domain.PageRequest.of(0, 20)).size());
    }

    @Test
    void itemSearchMatchesPartialNamesIgnoringCaseAndReturnsNoResultsForBlankQuery() {
        Item first = itemRepository.save(new Item("QueryMatch Headphones", new BigDecimal("10.00"), false, 5));
        Item second = itemRepository.save(new Item("querymatch speaker", new BigDecimal("12.00"), false, 5));
        itemRepository.save(new Item("Unrelated product", new BigDecimal("8.00"), false, 5));

        var matches = itemService.search("  QUERYMATCH ");

        assertEquals(2, matches.size());
        assertTrue(matches.stream().anyMatch(item -> item.getId().equals(first.getId())));
        assertTrue(matches.stream().anyMatch(item -> item.getId().equals(second.getId())));
        assertTrue(itemService.search("   ").isEmpty());
    }

    @Test
    void invalidProductRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/items")
                        .header("X-Forwarded-For", "49.206.129.249")
                        .contentType("application/json")
                        .content("{\"name\":\"Bad item\",\"price\":-1,\"highDemand\":false,\"stock\":-2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fixedDiscountIsAppliedOnceToEligibleCartTotal() {
        Item first = itemRepository.save(new Item("First item", new BigDecimal("20.00"), false, 5));
        Item second = itemRepository.save(new Item("Second item", new BigDecimal("20.00"), false, 5));
        cartService.add(301L, new CartRequest(first.getId(), 1));
        cartService.add(301L, new CartRequest(second.getId(), 1));
        var discount = discountService.create(new DiscountRequest(DiscountType.FIXED,
                new BigDecimal("5.00"), null, null, null));

        var result = orderService.checkout(301L, new CheckoutRequest("ORDER-FIXED-301", discount.getCode()));
        assertEquals(new BigDecimal("35.00"), result.order().getTotal());
    }

    @Test
    void updatingAndRemovingCartItemAdjustsReservedQuantity() {
        Item item = itemRepository.save(new Item("Popular item", new BigDecimal("25.00"), true, 5));
        cartService.add(401L, new CartRequest(item.getId(), 3));
        assertEquals(3, itemRepository.findById(item.getId()).orElseThrow().getReserved());

        cartService.update(401L, item.getId(), 1);
        assertEquals(1, itemRepository.findById(item.getId()).orElseThrow().getReserved());

        cartService.remove(401L, item.getId());
        assertEquals(0, itemRepository.findById(item.getId()).orElseThrow().getReserved());
    }

    @Test
    void checkoutRejectsAnExpiredHighDemandReservation() {
        Item item = itemRepository.save(new Item("Popular item", new BigDecimal("25.00"), true, 5));
        Cart cart = cartService.add(402L, new CartRequest(item.getId(), 1));
        cart.setReservedUntil(LocalDateTime.now().minusMinutes(1));

        assertThrows(IllegalArgumentException.class, () -> orderService.checkout(402L,
                new CheckoutRequest("ORDER-EXPIRED-402", null)));
        assertEquals(1, itemRepository.findById(item.getId()).orElseThrow().getReserved());
    }
}