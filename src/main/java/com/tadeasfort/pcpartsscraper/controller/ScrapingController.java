package com.tadeasfort.pcpartsscraper.controller;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.service.scraping.BazosScrapingService;
import com.tadeasfort.pcpartsscraper.service.scraping.PartTypeScrapingJob;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scraping")
@RequiredArgsConstructor
public class ScrapingController {

    private final BazosScrapingService bazosService;
    private final PartTypeScrapingJob partTypeScrapingJob;

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

    @GetMapping("/techpowerup/initial")
    public String runInitialTechPowerUpScraping() {
        try {
            partTypeScrapingJob.runInitialTechPowerUpScraping();
            return "Initial TechPowerUp scraping started successfully";
        } catch (Exception e) {
            return "TechPowerUp scraping failed: " + e.getMessage();
        }
    }

    @GetMapping("/components/update")
    public String updateComponentDatabase() {
        try {
            partTypeScrapingJob.updateComponentDatabase();
            return "Component database update started successfully";
        } catch (Exception e) {
            return "Component database update failed: " + e.getMessage();
        }
    }
}
