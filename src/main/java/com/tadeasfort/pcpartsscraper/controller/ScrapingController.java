package com.tadeasfort.pcpartsscraper.controller;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.service.scraping.BazosScrapingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scraping")
@RequiredArgsConstructor
public class ScrapingController {

    private final BazosScrapingService bazosService;

    @GetMapping("/bazos")
    public String scrapeBazos() {
        try {
            bazosService.scrapeAllCategories();
            return "Scraping started successfully for all categories";
        } catch (Exception e) {
            return "Scraping failed: " + e.getMessage();
        }
    }

    @GetMapping("/bazos/{partType}")
    public String scrapeBazosCategory(@PathVariable Part.PartType partType) {
        try {
            // Get the category path for this part type
            String categoryPath = getCategoryPath(partType);
            if (categoryPath == null) {
                return "Unknown part type: " + partType;
            }

            bazosService.scrapeCategory(partType, categoryPath);
            return "Scraping started successfully for category: " + partType.getDisplayName();
        } catch (Exception e) {
            return "Scraping failed for " + partType + ": " + e.getMessage();
        }
    }

    private String getCategoryPath(Part.PartType partType) {
        return switch (partType) {
            case GPU -> "graficka";
            case STORAGE_HDD -> "hdd";
            case CONSOLE -> "playstation";
            case COOLING -> "chladic";
            case KEYBOARD -> "klavesnice";
            case MONITOR -> "monitor";
            case MODEM -> "modem";
            case LAPTOP -> "notebook";
            case RAM -> "pamet";
            case OTHER -> "pc";
            case CPU -> "procesor";
            case NETWORKING -> "sit";
            case SCANNER -> "scaner";
            case CASE -> "case";
            case TABLET -> "tablet";
            case WIFI -> "wifi";
            case MOTHERBOARD -> "motherboard";
            case AUDIO_CARD -> "sound";
            default -> null;
        };
    }
}
