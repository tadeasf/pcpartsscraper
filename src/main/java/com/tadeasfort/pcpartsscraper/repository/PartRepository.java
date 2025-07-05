package com.tadeasfort.pcpartsscraper.repository;

import com.tadeasfort.pcpartsscraper.model.Part;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PartRepository extends JpaRepository<Part, Long> {

        Optional<Part> findByUniqueHash(String uniqueHash);

        boolean existsByUniqueHash(String uniqueHash);

        List<Part> findByMarketplaceAndExternalId(String marketplace, String externalId);

        // Batch methods for duplicate handling
        @Query("SELECT p.uniqueHash FROM Part p WHERE p.uniqueHash IN :uniqueHashes")
        Set<String> findExistingUniqueHashes(@Param("uniqueHashes") Set<String> uniqueHashes);

        Page<Part> findByActiveTrue(Pageable pageable);

        Page<Part> findByActiveTrueAndPartType(Part.PartType partType, Pageable pageable);

        // Simple methods that we can combine programmatically
        Page<Part> findByActiveTrueAndPartTypeAndPriceGreaterThanEqual(Part.PartType partType, BigDecimal minPrice,
                        Pageable pageable);

        Page<Part> findByActiveTrueAndPartTypeAndPriceLessThanEqual(Part.PartType partType, BigDecimal maxPrice,
                        Pageable pageable);

        Page<Part> findByActiveTrueAndPartTypeAndPriceBetween(Part.PartType partType, BigDecimal minPrice,
                        BigDecimal maxPrice, Pageable pageable);

        Page<Part> findByActiveTrueAndMarketplace(String marketplace, Pageable pageable);

        Page<Part> findByActiveTrueAndSource(String source, Pageable pageable);

        Page<Part> findByActiveTrueAndScrapedAtGreaterThanEqual(LocalDateTime scrapedAt, Pageable pageable);

        // Search methods
        @Query("SELECT p FROM Part p WHERE p.active = true AND " +
                        "(LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(COALESCE(p.model, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
        Page<Part> findByActiveTrueAndSearchTerm(@Param("search") String search, Pageable pageable);

        @Query("SELECT DISTINCT p.source FROM Part p WHERE p.active = true ORDER BY p.source")
        List<String> findDistinctSources();

        @Query("SELECT DISTINCT p.marketplace FROM Part p WHERE p.active = true ORDER BY p.marketplace")
        List<String> findDistinctMarketplaces();

        @Query("SELECT DISTINCT p.itemType FROM Part p WHERE p.active = true AND p.itemType IS NOT NULL ORDER BY p.itemType")
        List<String> findDistinctItemTypes();

        @Query("SELECT DISTINCT p.modelName FROM Part p WHERE p.active = true AND p.modelName IS NOT NULL ORDER BY p.modelName")
        List<String> findDistinctModelNames();

        @Query("SELECT DISTINCT p.modelName FROM Part p WHERE p.active = true AND p.itemType = :itemType AND p.modelName IS NOT NULL ORDER BY p.modelName")
        List<String> findDistinctModelNamesByItemType(@Param("itemType") String itemType);

        @Query("SELECT COUNT(p) FROM Part p WHERE p.active = true AND p.scrapedAt >= :since")
        long countNewPartsScrapedSince(@Param("since") LocalDateTime since);

        @Query("SELECT p.partType, COUNT(p) FROM Part p WHERE p.active = true GROUP BY p.partType")
        List<Object[]> getPartCountsByType();

        @Query("SELECT p.marketplace, COUNT(p) FROM Part p WHERE p.active = true GROUP BY p.marketplace")
        List<Object[]> getPartCountsByMarketplace();

        @Query("SELECT p FROM Part p WHERE p.active = true ORDER BY p.scrapedAt DESC")
        List<Part> findLatestParts(Pageable pageable);

        // Add count method for active parts
        long countByActiveTrue();

        // Methods for component type extraction
        List<Part> findByItemTypeIsNull();

        List<Part> findByItemTypeIsNotNull();

        List<Part> findByItemType(String itemType);

        List<Part> findByModelName(String modelName);

        Page<Part> findByActiveTrueAndItemType(String itemType, Pageable pageable);

        Page<Part> findByActiveTrueAndModelName(String modelName, Pageable pageable);
}