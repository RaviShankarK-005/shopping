package com.example.shopping;

import com.example.shopping.discount.Discount;
import com.example.shopping.discount.DiscountRepository;
import com.example.shopping.discount.DiscountType;
import com.example.shopping.item.Item;
import com.example.shopping.item.ItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Adds demonstration products and discounts when the product table is empty.
 */
@Configuration
public class SampleDataConfig {

    /**
     * Creates the startup runner that populates the in-memory demonstration data.
     *
     * @param itemRepository repository used to check and save products
     * @param discountRepository repository used to save discounts
     * @return the startup data loader
     */
    @Bean
    CommandLineRunner loadSampleData(ItemRepository itemRepository, DiscountRepository discountRepository) {
        return args -> {
            // H2 is recreated on every start, so add demo data only when the tables are empty.
            if (itemRepository.count() == 0) {
                List<Item> items = itemRepository.saveAll(List.of(
                        new Item("Wireless Headphones", new BigDecimal("79.99"), true, 25),
                        new Item("Smart Watch", new BigDecimal("129.99"), true, 15),
                        new Item("Running Shoes", new BigDecimal("64.50"), true, 30),
                        new Item("Coffee Maker", new BigDecimal("49.99"), false, 20),
                        new Item("Travel Backpack", new BigDecimal("39.99"), false, 40),
                        new Item("Bluetooth Speaker", new BigDecimal("34.99"), true, 18),
                        new Item("Desk Lamp", new BigDecimal("22.50"), false, 35),
                        new Item("Water Bottle", new BigDecimal("14.99"), false, 50),
                        new Item("Fitness Tracker", new BigDecimal("89.00"), false, 12),
                        new Item("Laptop Stand", new BigDecimal("27.99"), false, 22)
                ));

                LocalDateTime now = LocalDateTime.now();
                discountRepository.saveAll(List.of(
                        new Discount("WELCOME10", DiscountType.PERCENT, new BigDecimal("10"), null, now.minusDays(1), now.plusDays(30)),
                        new Discount("AUDIO15", DiscountType.PERCENT, new BigDecimal("15"), items.get(0).getId(), now.minusDays(1), now.plusDays(14)),
                        new Discount("SAVE5", DiscountType.FIXED, new BigDecimal("5"), null, now.minusDays(1), now.plusDays(7)),
                        new Discount("WATCH20", DiscountType.PERCENT, new BigDecimal("20"), items.get(1).getId(), now.minusDays(1), now.plusDays(10))
                ));
            }
        };
    }
}
