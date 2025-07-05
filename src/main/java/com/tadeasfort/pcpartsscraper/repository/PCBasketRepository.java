package com.tadeasfort.pcpartsscraper.repository;

import com.tadeasfort.pcpartsscraper.model.PCBasket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PCBasketRepository extends JpaRepository<PCBasket, Long> {

    @Query("SELECT b FROM PCBasket b LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.part WHERE b.active = true ORDER BY b.createdAt DESC")
    List<PCBasket> findAllByActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT b FROM PCBasket b LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.part WHERE b.id = :id AND b.active = true")
    Optional<PCBasket> findByIdAndActiveTrue(@Param("id") Long id);

    @Query("SELECT COUNT(b) FROM PCBasket b WHERE b.active = true")
    long countActiveBaskets();

    @Query("SELECT b FROM PCBasket b LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.part WHERE b.active = true AND " +
            "(:searchTerm IS NULL OR :searchTerm = '' OR " +
            " LOWER(COALESCE(b.name, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<PCBasket> findBasketsWithSearch(@Param("searchTerm") String searchTerm);
}