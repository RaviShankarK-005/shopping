package com.example.shopping.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Persistence queries for orders and user order history. */
public interface OrderRepository extends JpaRepository<Order, String> {
    /** Returns a user's orders from newest to oldest. */
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
