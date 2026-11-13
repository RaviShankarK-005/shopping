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
import com.example.shopping.order.CheckoutRequest;
import com.example.shopping.order.OrderRepository;
import com.example.shopping.order.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deliberately NOT @Transactional: every call commits for real, so these tests exercise the row locks,
 * the per-row expiry transactions and the checkout ordering the way concurrent requests would.
 */
@SpringBootTest
class ConcurrencyAndIntegrityTests {

    @Autowired private ItemRepository itemRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private DiscountService discountService;

    @BeforeEach @AfterEach
    void clean() {
        cartRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private Item item(String name, String price, boolean highDemand, int stock) {
        return itemRepository.save(new Item(name, new BigDecimal(price), highDemand, stock));
    }

    private Item reload(Item item) { return itemRepository.findById(item.getId()).orElseThrow(); }

    private void expire(Long userId, Long itemId) {
        Cart cart = cartRepository.findByIdUserIdAndIdItemId(userId, itemId).orElseThrow();
        cart.setReservedUntil(LocalDateTime.now().minusMinutes(1));
        cartRepository.save(cart);
    }

    @Test
    void twentyUsersRaceForFiveUnitsExactlyFiveWin() throws Exception {
        Item item = item("Race item", "25.00", true, 5);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), rejected = new AtomicInteger(), unexpected = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (long user = 1000; user < 1020; user++) {
            long userId = user;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    cartService.add(userId, new CartRequest(item.getId(), 1));
                    ok.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    rejected.incrementAndGet();          // "Not enough stock available"
                } catch (Exception e) {
                    unexpected.incrementAndGet();        // lock timeout, deadlock, ...
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, unexpected.get());
        assertEquals(5, ok.get());
        assertEquals(15, rejected.get());
        assertEquals(5, reload(item).getReserved());
    }

    @Test
    void addAndRemoveCyclesNeverLeakReservedStock() throws Exception {
        Item item = item("Cycle item", "10.00", true, 3);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger unexpected = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (long user = 2000; user < 2010; user++) {
            long userId = user;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 20; i++) {
                    try {
                        cartService.add(userId, new CartRequest(item.getId(), 1));
                        cartService.remove(userId, item.getId());
                    } catch (IllegalArgumentException ignored) {
                        // someone else holds the units right now
                    } catch (Exception e) {
                        unexpected.incrementAndGet();
                    }
                }
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, unexpected.get());
        assertEquals(0, reload(item).getReserved());
        assertTrue(cartRepository.findAll().isEmpty());
    }

    @Test
    void normalItemCannotBeOverfilledInTheBag() {
        Item item = item("Normal item", "10.00", false, 3);

        cartService.add(1L, new CartRequest(item.getId(), 3));
        assertThrows(IllegalArgumentException.class, () -> cartService.add(1L, new CartRequest(item.getId(), 1)));
        assertThrows(IllegalArgumentException.class, () -> cartService.add(2L, new CartRequest(item.getId(), 4)));
        assertThrows(IllegalArgumentException.class, () -> cartService.update(1L, item.getId(), 4));

        cartService.update(1L, item.getId(), 2);   // lowering is fine
        assertEquals(2, cartRepository.findByIdUserIdAndIdItemId(1L, item.getId()).orElseThrow().getQuantity());
        assertEquals(0, reload(item).getReserved());   // normal items are never reserved
    }

    @Test
    void addingAgainAfterExpiryReusesTheRowAndRestartsTheTimer() {
        Item item = item("Expiring item", "25.00", true, 5);
        cartService.add(3L, new CartRequest(item.getId(), 2));
        expire(3L, item.getId());

        cartService.add(3L, new CartRequest(item.getId(), 1));   // must not hit a unique violation

        Cart cart = cartRepository.findByIdUserIdAndIdItemId(3L, item.getId()).orElseThrow();
        assertEquals(1, cart.getQuantity());
        assertTrue(cart.getReservedUntil().isAfter(LocalDateTime.now().plusMinutes(14)));
        assertEquals(1, reload(item).getReserved());   // the old 2 were released, the new 1 reserved
    }

    @Test
    void changingTheQuantityDoesNotExtendTheReservation() {
        Item item = item("Timer item", "25.00", true, 5);
        cartService.add(4L, new CartRequest(item.getId(), 1));

        LocalDateTime fiveMinutesLeft = LocalDateTime.now().plusMinutes(5).truncatedTo(ChronoUnit.MICROS);
        Cart cart = cartRepository.findByIdUserIdAndIdItemId(4L, item.getId()).orElseThrow();
        cart.setReservedUntil(fiveMinutesLeft);
        cartRepository.save(cart);

        cartService.update(4L, item.getId(), 2);
        cartService.add(4L, new CartRequest(item.getId(), 1));

        Cart after = cartRepository.findByIdUserIdAndIdItemId(4L, item.getId()).orElseThrow();
        assertEquals(3, after.getQuantity());
        assertEquals(fiveMinutesLeft, after.getReservedUntil());
    }

    @Test
    void expiryJobReleasesOnlyExpiredRows() {
        Item item = item("Sweep item", "25.00", true, 5);
        cartService.add(5L, new CartRequest(item.getId(), 2));
        cartService.add(6L, new CartRequest(item.getId(), 1));
        expire(5L, item.getId());

        cartService.expireReservations();

        assertEquals(1, reload(item).getReserved());
        assertTrue(cartRepository.findByIdUserIdAndIdItemId(5L, item.getId()).isEmpty());
        assertTrue(cartRepository.findByIdUserIdAndIdItemId(6L, item.getId()).isPresent());
    }

    @Test
    void percentageDiscountAboveOneHundredIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> discountService.create(
                new DiscountRequest(DiscountType.PERCENT, new BigDecimal("150"), null, null, null)));
    }

    @Test
    void fixedDiscountOnOneItemNeverExceedsThatItemsPrice() {
        Item cheap = item("Cheap item", "10.00", false, 5);
        Item other = item("Other item", "30.00", false, 5);
        cartService.add(7L, new CartRequest(cheap.getId(), 1));
        cartService.add(7L, new CartRequest(other.getId(), 1));
        var discount = discountService.create(new DiscountRequest(
                DiscountType.FIXED, new BigDecimal("20.00"), cheap.getId(), null, null));

        var result = orderService.checkout(7L, new CheckoutRequest("ORDER-CAP-7", discount.getCode()));

        assertEquals(new BigDecimal("30.00"), result.order().getTotal());   // 40.00 - min(20, 10)
    }

    @Test
    void percentageDiscountTotalIsRoundedToPence() {
        Item item = item("Rounded item", "79.99", false, 5);
        cartService.add(8L, new CartRequest(item.getId(), 1));
        var discount = discountService.create(new DiscountRequest(
                DiscountType.PERCENT, new BigDecimal("15"), null, null, null));

        var result = orderService.checkout(8L, new CheckoutRequest("ORDER-ROUND-8", discount.getCode()));

        assertEquals(new BigDecimal("67.99"), result.order().getTotal());   // 67.9915 rounded
    }

    @Test
    void discountsWithoutDatesAreAlwaysAvailable() {
        var open = discountService.create(new DiscountRequest(
                DiscountType.PERCENT, new BigDecimal("5"), null, null, null));
        assertTrue(discountService.getAvailable().stream().anyMatch(d -> d.getCode().equals(open.getCode())));
    }
}
