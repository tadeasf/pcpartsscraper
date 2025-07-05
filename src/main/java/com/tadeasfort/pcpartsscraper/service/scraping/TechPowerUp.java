package com.tadeasfort.pcpartsscraper.service.scraping;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

public class TechPowerUp {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentInfo {
        private String name;
        private String codename;
        private String itemType; // GPU, CPU
        private String brand; // NVIDIA, AMD, Intel
        private String series; // RTX 50, Ryzen 5000
        private Integer releaseYear;
        private LocalDateTime releaseDate;
        private String socket;
        private String process;
        private String specifications;
        private String techPowerUpUrl;

        // GPU specific
        private String memory;
        private String memoryType;
        private String busWidth;

        // CPU specific
        private Integer cores;
        private Integer threads;
        private String baseClockSpeed;
        private String boostClockSpeed;
        private String cache;
        private String tdp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrapingResult {
        private String componentType; // gpu, cpu
        private Integer year;
        private Integer totalComponents;
        private java.util.List<ComponentInfo> components;
        private boolean successful;
        private String errorMessage;
        private LocalDateTime scrapedAt;
    }
}
