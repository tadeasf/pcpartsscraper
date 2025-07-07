package com.tadeasfort.pcpartsscraper.controller;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.model.PartType;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import com.tadeasfort.pcpartsscraper.repository.PartTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiController {

    private final PartRepository partRepository;
    private final PartTypeRepository partTypeRepository;

    @GetMapping("/models")
    public ResponseEntity<List<String>> getModelsByItemType(@RequestParam(required = false) String itemType) {
        try {
            List<String> models;
            if (itemType != null && !itemType.trim().isEmpty()) {
                models = partRepository.findDistinctModelNamesByItemTypeOrderByModelName(itemType);
                log.debug("Found {} models for itemType: {}", models.size(), itemType);
            } else {
                models = partRepository.findDistinctModelNames();
                log.debug("Found {} total models (no itemType specified)", models.size());
            }
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            log.error("Error fetching models for item type {}: {}", itemType, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/price-statistics")
    public ResponseEntity<Map<String, Object>> getPriceStatistics(
            @RequestParam String modelName,
            @RequestParam String itemType) {

        try {
            // Get statistics from the database
            Optional<PartType> partTypeOpt = partTypeRepository.findByItemTypeAndModelName(itemType, modelName);

            // Get all prices for this model to calculate distribution
            List<Part> parts = partRepository.findByModelNameAndItemTypeAndPriceIsNotNullAndActive(modelName, itemType,
                    true);
            List<BigDecimal> prices = parts.stream()
                    .map(Part::getPrice)
                    .collect(Collectors.toList());

            // Convert BigDecimal prices to doubles for easier JS handling
            List<Double> pricesDouble = prices.stream()
                    .map(BigDecimal::doubleValue)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();

            if (partTypeOpt.isPresent()) {
                PartType partType = partTypeOpt.get();

                result.put("modelName", modelName);
                result.put("itemType", itemType);
                result.put("averagePrice",
                        partType.getAveragePrice() != null ? partType.getAveragePrice().doubleValue() : 0);
                result.put("medianPrice",
                        partType.getMedianPrice() != null ? partType.getMedianPrice().doubleValue() : 0);
                result.put("minPrice", partType.getMinPrice() != null ? partType.getMinPrice().doubleValue() : 0);
                result.put("maxPrice", partType.getMaxPrice() != null ? partType.getMaxPrice().doubleValue() : 0);
                result.put("totalListings", partType.getTotalListings());
                result.put("activeListings", partType.getActiveListings());
                result.put("allPrices", pricesDouble); // For histogram chart
                result.put("success", true);

                return ResponseEntity.ok(result);
            } else {
                // If no statistics in database, calculate from available parts
                if (!prices.isEmpty()) {
                    BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal avg = sum.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);

                    // Sort prices for median and min/max
                    prices.sort(BigDecimal::compareTo);

                    BigDecimal median;
                    int size = prices.size();
                    if (size % 2 == 0) {
                        median = prices.get(size / 2 - 1).add(prices.get(size / 2))
                                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    } else {
                        median = prices.get(size / 2);
                    }

                    result.put("modelName", modelName);
                    result.put("itemType", itemType);
                    result.put("averagePrice", avg.doubleValue());
                    result.put("medianPrice", median.doubleValue());
                    result.put("minPrice", prices.get(0).doubleValue());
                    result.put("maxPrice", prices.get(size - 1).doubleValue());
                    result.put("totalListings", prices.size());
                    result.put("activeListings", prices.size());
                    result.put("allPrices", pricesDouble);
                    result.put("success", true);

                    log.info("Calculated on-demand statistics for {}: avg={}, median={}, count={}",
                            modelName, avg, median, prices.size());

                    return ResponseEntity.ok(result);
                } else {
                    result.put("success", false);
                    result.put("message", "No price data available for " + modelName);
                    return ResponseEntity.ok(result);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching price statistics for {}/{}: {}", itemType, modelName, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error fetching price statistics: " + e.getMessage());
            return ResponseEntity.ok(error);
        }
    }
}