package com.tadeasfort.pcpartsscraper.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parts", indexes = {
        @Index(name = "idx_part_type", columnList = "partType"),
        @Index(name = "idx_marketplace", columnList = "marketplace"),
        @Index(name = "idx_price", columnList = "price"),
        @Index(name = "idx_scraped_at", columnList = "scrapedAt"),
        @Index(name = "idx_external_id", columnList = "externalId"),
        @Index(name = "idx_unique_hash", columnList = "uniqueHash"),
        @Index(name = "idx_item_type", columnList = "itemType"),
        @Index(name = "idx_model_name", columnList = "modelName"),
        @Index(name = "idx_parts_category", columnList = "category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "uniqueHash")
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartType partType;

    @Column(nullable = true, precision = 10, scale = 2)
    @PositiveOrZero
    private BigDecimal price; // Null when price is "v textu" (negotiable/contact seller)

    @Builder.Default
    @Column(length = 3)
    private String currency = "CZK";

    @NotBlank
    @Column(nullable = false, length = 50)
    private String marketplace;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String source; // Specific source within marketplace (e.g., "bazos", "sbazar")

    @NotBlank
    @Column(nullable = false, length = 50)
    private String externalId;

    @NotBlank
    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 200)
    private String location;

    @Column(length = 100)
    private String condition;

    @Column(length = 100)
    private String brand;

    @Column(length = 200)
    private String model;

    // New fields for component type detection
    @Column(length = 100)
    private String itemType; // GPU, CPU, RAM, etc.

    @Column(length = 200)
    private String modelName; // "RTX 5070", "Ryzen 5 5600X", etc.

    @Column(length = 50)
    private String category; // More specific: "graphics_card", "processor", "memory"

    @Column(precision = 3, scale = 2)
    private BigDecimal extractionConfidence; // 0.0-1.0 confidence score for extraction

    @Column(length = 200)
    private String sellerName;

    @Column(length = 100)
    private String phone;

    @Column
    private Integer viewCount;

    @Column
    private Boolean isPromoted;

    @NotNull
    @Column(nullable = false, updatable = false)
    private LocalDateTime scrapedAt;

    @Column
    private LocalDateTime updatedAt;

    @NotBlank
    @Column(nullable = false, unique = true, length = 64)
    private String uniqueHash;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @PrePersist
    protected void onCreate() {
        scrapedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PartType {
        CPU("Procesory"),
        GPU("Grafické karty"),
        MOTHERBOARD("Základní desky"),
        RAM("Paměti RAM"),
        STORAGE_SSD("SSD disky"),
        STORAGE_HDD("HDD disky"),
        PSU("Zdroje"),
        CASE("Skříně"),
        COOLING("Chlazení"),
        MONITOR("Monitory"),
        KEYBOARD("Klávesnice"),
        MOUSE("Myši"),
        LAPTOP("Notebooky"),
        MODEM("Modemy"),
        CONSOLE("Konzole"),
        NETWORKING("Síťové prvky"),
        SCANNER("Scanery"),
        TABLET("Tablety"),
        WIFI("WiFi"),
        AUDIO_CARD("Zvukové karty"),
        OTHER("Ostatní");

        private final String displayName;

        PartType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
