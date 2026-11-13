package com.example.shopping.discount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Discount persistence queries, including case-insensitive code lookup.
 */
public interface DiscountRepository extends JpaRepository<Discount, Long> {
    /** Finds a discount code without case sensitivity. */
    Optional<Discount> findByCodeIgnoreCase(String code);

    /** Finds discounts valid at the supplied time. */
    @Query("select d from Discount d where (d.validFrom is null or d.validFrom <= :now) "
         + "and (d.validTo is null or d.validTo >= :now)")
    List<Discount> findAvailableAt(@Param("now") java.time.LocalDateTime now);
    List<Discount> findByValidFromLessThanEqualAndValidToGreaterThanEqual(
            java.time.LocalDateTime from, java.time.LocalDateTime to);
}
