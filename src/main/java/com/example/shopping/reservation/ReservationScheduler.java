package com.example.shopping.reservation;

import com.example.shopping.cart.CartService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically invokes cleanup for expired high-demand stock reservations.
 */
@Component
public class ReservationScheduler {
    private final CartService cartService;

    public ReservationScheduler(CartService cartService) { this.cartService = cartService; }

    /** Removes cart rows whose reservation deadlines have passed. */
    @Scheduled(fixedDelay = 30000)
    public void removeExpiredReservations() {
        cartService.expireReservations();
    }
}
