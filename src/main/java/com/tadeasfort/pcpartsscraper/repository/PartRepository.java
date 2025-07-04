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

        @Query("SELECT p FROM Part p WHERE p.active = true AND " +
                        "(:partType IS NULL OR p.partType = :partType) AND " +
                        "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
                        "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
                        "(:marketplace IS NULL OR p.marketplace = :marketplace) AND " +
                        "(:searchTerm IS NULL OR :searchTerm = '' OR " +
                        " LOWER(COALESCE(p.title, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                        " LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
        Page<Part> findWithFilters(
                        @Param("partType") Part.PartType partType,
                        @Param("minPrice") BigDecimal minPrice,
                        @Param("maxPrice") BigDecimal maxPrice,
                        @Param("marketplace") String marketplace,
                        @Param("searchTerm") String searchTerm,
                        Pageable pageable);

        @Query("SELECT COUNT(p) FROM Part p WHERE p.active = true AND p.scrapedAt >= :since")
        long countNewPartsScrapedSince(@Param("since") LocalDateTime since);

        @Query("SELECT p.partType, COUNT(p) FROM Part p WHERE p.active = true GROUP BY p.partType")
        List<Object[]> getPartCountsByType();

        @Query("SELECT p.marketplace, COUNT(p) FROM Part p WHERE p.active = true GROUP BY p.marketplace")
        List<Object[]> getPartCountsByMarketplace();

        @Query("SELECT p FROM Part p WHERE p.active = true ORDER BY p.scrapedAt DESC")
        List<Part> findLatestParts(Pageable pageable);
}