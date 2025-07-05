package com.tadeasfort.pcpartsscraper.service;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.model.PartType;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import com.tadeasfort.pcpartsscraper.repository.PartTypeRepository;
import com.tadeasfort.pcpartsscraper.service.scraping.TechPowerUp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentExtractionService {

    private final PartRepository partRepository;
    private final PartTypeRepository partTypeRepository;

    // In-memory cache of known components from TechPowerUp
    private final Map<String, TechPowerUp.ComponentInfo> knownComponents = new HashMap<>();

    // Regex patterns for component extraction
    private static final Pattern GPU_PATTERN = Pattern.compile(
            "(?i)(RTX|GTX|RX)\\s*(\\d{4})\\s*(Ti|Super|XT|XTX)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CPU_PATTERN = Pattern.compile(
            "(?i)(i[3579]|Ryzen\\s*[3579])\\s*([A-Z]*\\d{4}[A-Z]*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RAM_PATTERN = Pattern.compile(
            "(?i)(DDR[345])\\s*(\\d+)\\s*GB|(?i)(\\d+)\\s*GB\\s*(DDR[345])",
            Pattern.CASE_INSENSITIVE);

    @Transactional
    public void extractAndUpdatePartInfo(Part part) {
        if (part.getItemType() != null) {
            return; // Already processed
        }

        String title = part.getTitle();
        if (title == null || title.trim().isEmpty()) {
            return;
        }

        // Try to extract component information
        ComponentMatch match = extractComponentInfo(title, part.getPartType());

        if (match != null) {
            part.setItemType(match.itemType);
            part.setModelName(match.modelName);
            part.setCategory(match.category);
            part.setExtractionConfidence(BigDecimal.valueOf(match.confidence));

            partRepository.save(part);

            log.debug("Extracted component info for part {}: {} - {}",
                    part.getId(), match.itemType, match.modelName);
        }
    }

    private ComponentMatch extractComponentInfo(String title, Part.PartType partType) {
        // GPU extraction
        if (partType == Part.PartType.GPU) {
            Matcher matcher = GPU_PATTERN.matcher(title);
            if (matcher.find()) {
                String series = matcher.group(1).toUpperCase();
                String model = matcher.group(2);
                String variant = matcher.group(3);

                String fullModel = series + " " + model;
                if (variant != null) {
                    fullModel += " " + variant;
                }

                return new ComponentMatch(
                        "GPU",
                        fullModel,
                        "graphics_card",
                        0.8 // High confidence for clear GPU patterns
                );
            }
        }

        // CPU extraction
        if (partType == Part.PartType.CPU) {
            Matcher matcher = CPU_PATTERN.matcher(title);
            if (matcher.find()) {
                String series = matcher.group(1);
                String model = matcher.group(2);

                String fullModel = series + " " + model;

                return new ComponentMatch(
                        "CPU",
                        fullModel,
                        "processor",
                        0.8 // High confidence for clear CPU patterns
                );
            }
        }

        // RAM extraction
        if (partType == Part.PartType.RAM) {
            Matcher matcher = RAM_PATTERN.matcher(title);
            if (matcher.find()) {
                String ddrType = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
                String capacity = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);

                String fullModel = ddrType.toUpperCase() + " " + capacity + "GB";

                return new ComponentMatch(
                        "RAM",
                        fullModel,
                        "memory",
                        0.9 // Very high confidence for RAM patterns
                );
            }
        }

        return null;
    }

    public void addKnownComponent(TechPowerUp.ComponentInfo component) {
        knownComponents.put(component.getName().toLowerCase(), component);
    }

    public void updatePriceStatistics(String modelName, String itemType, String category,
            BigDecimal averagePrice, BigDecimal medianPrice,
            BigDecimal minPrice, BigDecimal maxPrice,
            int totalListings, int activeListings) {

        // Find or create PartType entity
        PartType partType = partTypeRepository.findByItemTypeAndModelName(itemType, modelName)
                .orElse(PartType.builder()
                        .itemType(itemType)
                        .modelName(modelName)
                        .category(category)
                        .build());

        // Update statistics
        partType.setAveragePrice(averagePrice);
        partType.setMedianPrice(medianPrice);
        partType.setMinPrice(minPrice);
        partType.setMaxPrice(maxPrice);
        partType.setTotalListings(totalListings);
        partType.setActiveListings(activeListings);

        partTypeRepository.save(partType);

        log.info("Updated price statistics for {}: avg={}, median={}, min={}, max={}, total={}, active={}",
                modelName, averagePrice, medianPrice, minPrice, maxPrice, totalListings, activeListings);
    }

    private static class ComponentMatch {
        final String itemType;
        final String modelName;
        final String category;
        final double confidence;

        ComponentMatch(String itemType, String modelName, String category, double confidence) {
            this.itemType = itemType;
            this.modelName = modelName;
            this.category = category;
            this.confidence = confidence;
        }
    }
}