package com.example.shopping.item;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Product persistence queries, including ordered dashboards and row locking.
 */
public interface ItemRepository extends JpaRepository<Item, Long> {
    /** Finds products whose names contain the supplied text, ignoring case. */
    List<Item> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    /** Returns high-demand products ordered newest first. */
	@Query("SELECT i FROM Item i WHERE i.highDemand = true ORDER BY i.listedAt DESC")
    List<Item> findPopularItems(Pageable pageable);

    /** Returns products ordered newest first. */
    @Query("SELECT i FROM Item i ORDER BY i.listedAt DESC")
    List<Item> findNewItems(Pageable pageable);

    /** Loads and exclusively locks a product row for a stock-changing operation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);
}
