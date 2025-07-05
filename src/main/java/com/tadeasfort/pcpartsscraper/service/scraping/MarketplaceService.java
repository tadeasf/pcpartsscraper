package com.tadeasfort.pcpartsscraper.service.scraping;

import com.tadeasfort.pcpartsscraper.model.Part;

/**
 * Interface for marketplace scraping services
 */
public interface MarketplaceService {

    /**
     * Scrape a specific category from the marketplace
     * 
     * @param partType     The type of part to scrape
     * @param categoryPath The marketplace-specific category path
     */
    void scrapeCategory(Part.PartType partType, String categoryPath);

    /**
     * Scrape all categories from the marketplace
     */
    void scrapeAllCategories();

    /**
     * Get the name of the marketplace
     * 
     * @return marketplace name
     */
    String getMarketplaceName();

    /**
     * Check if scraping is enabled for this marketplace
     * 
     * @return true if enabled, false otherwise
     */
    boolean isScrapingEnabled();
}