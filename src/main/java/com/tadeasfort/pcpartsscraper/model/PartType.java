package com.tadeasfort.pcpartsscraper.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "part_types", indexes = {
        @Index(name = "idx_item_type_model", columnList = "itemType, modelName"),
        @Index(name = "idx_part_types_category", columnList = "category"),
        @Index(name = "idx_updated_at", columnList = "updatedAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String itemType; // GPU, CPU, RAM

    @Column(nullable = false, length = 200)
    private String modelName; // "RTX 5070", "Ryzen 5 5600X"

    @Column(length = 50)
    private String category; // "graphics_card", "processor", "memory"

    @Column(length = 100)
    private String brand; // "NVIDIA", "AMD", "Intel"

    @Column(length = 100)
    private String series; // "RTX 50", "Ryzen 5000"

    @Column
    private Integer releaseYear;

    @Column
    private LocalDateTime releaseDate;

    // Price statistics
    @Column(precision = 10, scale = 2)
    private BigDecimal averagePrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal medianPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal minPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxPrice;

    @Column
    private Integer totalListings;

    @Column
    private Integer activeListings;

    // Technical specifications (optional, for display)
    @Column(columnDefinition = "TEXT")
    private String specifications;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
