package com.tadeasfort.pcpartsscraper.service.scraping;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import com.tadeasfort.pcpartsscraper.service.TorProxyService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;

@Service
@Slf4j
public class BazosScrapingService implements MarketplaceService {

    private final PartRepository partRepository;
    private final TorProxyService torProxyService;

    public BazosScrapingService(PartRepository partRepository, TorProxyService torProxyService) {
        this.partRepository = partRepository;
        this.torProxyService = torProxyService;
    }

    private static final String BASE_URL = "https://pc.bazos.cz";
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(?:\\s*\\d*)*)\\s*Kč");
    private static final Pattern PRICE_IN_TEXT_PATTERN = Pattern
            .compile("(?i)cena\\s*[:\\-]?\\s*(\\d+(?:\\s*\\d*)*)\\s*(?:kč|czk|,-)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_PATTERN = Pattern.compile("/inzerat/(\\d+)/");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("([A-Za-z\\s]+)(\\d{3}\\s*\\d{2})");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\[(\\d+\\.\\d+\\.\\s*\\d+)\\]");

    @Value("${app.scraping.enabled:true}")
    private boolean scrapingEnabled;

    @Value("${app.scraping.bazos.duplicate-stop-threshold:0.8}")
    private double duplicateStopThreshold;

    @Override
    public String getMarketplaceName() {
        return "bazos";
    }

    @Override
    public boolean isScrapingEnabled() {
        return scrapingEnabled;
    }

    // Updated category mappings based on actual Bazos.cz URLs
    private static final Map<Part.PartType, String> CATEGORY_MAPPINGS = new HashMap<>();
    static {
        CATEGORY_MAPPINGS.put(Part.PartType.GPU, "graficka");
        CATEGORY_MAPPINGS.put(Part.PartType.STORAGE_HDD, "hdd"); // drives (hdd/ssd)
        CATEGORY_MAPPINGS.put(Part.PartType.CONSOLE, "playstation"); // consoles
        CATEGORY_MAPPINGS.put(Part.PartType.COOLING, "chladic"); // coolers
        CATEGORY_MAPPINGS.put(Part.PartType.KEYBOARD, "klavesnice"); // keyboards
        CATEGORY_MAPPINGS.put(Part.PartType.MONITOR, "monitor"); // monitors
        CATEGORY_MAPPINGS.put(Part.PartType.MODEM, "modem"); // modems
        CATEGORY_MAPPINGS.put(Part.PartType.LAPTOP, "notebook"); // laptops
        CATEGORY_MAPPINGS.put(Part.PartType.RAM, "pamet"); // ram
        CATEGORY_MAPPINGS.put(Part.PartType.OTHER, "pc"); // pre-builts -> OTHER
        CATEGORY_MAPPINGS.put(Part.PartType.CPU, "procesor"); // cpus
        CATEGORY_MAPPINGS.put(Part.PartType.NETWORKING, "sit"); // networking
        CATEGORY_MAPPINGS.put(Part.PartType.SCANNER, "scaner"); // scanners
        CATEGORY_MAPPINGS.put(Part.PartType.CASE, "case"); // cases/power supplies
        CATEGORY_MAPPINGS.put(Part.PartType.TABLET, "tablet"); // ebook readers/tablets
        CATEGORY_MAPPINGS.put(Part.PartType.WIFI, "wifi"); // wifi
        CATEGORY_MAPPINGS.put(Part.PartType.MOTHERBOARD, "motherboard"); // mobos
        CATEGORY_MAPPINGS.put(Part.PartType.AUDIO_CARD, "sound"); // audio cards
    }

    // Reuse connection for better performance
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int CONNECT_TIMEOUT = 10000; // Reduced from 15000 - this applies to both connect and read
                                                      // timeouts

    public void scrapeCategory(Part.PartType partType, String categoryPath) {
        if (!scrapingEnabled) {
            log.info("Scraping is disabled, skipping {}", partType);
            return;
        }

        log.info("Starting scraping for category: {} at path: {}", partType, categoryPath);

        try {
            int page = 1;
            boolean hasMorePages = true;
            int totalScraped = 0;

            while (hasMorePages && page <= 500) { // Safety limit to prevent infinite loops
                String url = buildUrl(categoryPath, page);
                log.debug("Scraping page {} for {}: {}", page, partType, url);

                Document doc = createJsoupConnection(url).get();

                doc.body().text();
                List<String> listingUrls = extractListingUrls(doc);

                if (listingUrls.isEmpty()) {
                    log.info("No more listings found on page {}, stopping", page);
                    hasMorePages = false;
                    break;
                }

                // Process each listing URL to get detailed data
                List<Part> pageScrapedParts = new ArrayList<>();
                for (String listingUrl : listingUrls) {
                    try {
                        Part part = scrapeIndividualListing(listingUrl, partType);
                        if (part != null) {
                            pageScrapedParts.add(part);
                        }
                        Thread.sleep(200); // Reduced from 500ms to 200ms for better performance
                    } catch (Exception e) {
                        log.warn("Error scraping individual listing {}: {}", listingUrl, e.getMessage());
                    }
                }

                // Save parts for this page in a separate transaction
                SaveResult result = new SaveResult(0, 0, 0);
                if (!pageScrapedParts.isEmpty()) {
                    result = savePartsInTransaction(pageScrapedParts);
                    totalScraped += result.saved;
                    log.debug("Page {}: Saved {} parts, skipped {} duplicates", page, result.saved,
                            result.getTotalDuplicates());
                }

                // Check for early termination conditions
                // Only consider database duplicates for termination, not intra-batch duplicates
                boolean shouldStopEarly = false;
                if (!pageScrapedParts.isEmpty()) {
                    // Calculate database duplicate ratio (parts already in DB vs unique parts from
                    // page)
                    int uniquePartsFromPage = pageScrapedParts.size() - result.intraBatchDuplicates;
                    double databaseDuplicateRatio = uniquePartsFromPage > 0
                            ? (double) result.databaseDuplicates / uniquePartsFromPage
                            : 0.0;

                    if (databaseDuplicateRatio >= duplicateStopThreshold) {
                        log.info("Stopping scraping for {} - database duplicate ratio {:.1%} exceeds threshold {:.1%}",
                                partType, databaseDuplicateRatio, duplicateStopThreshold);
                        shouldStopEarly = true;
                    }
                }

                // Check if there's a next page by looking for "Další" link in DOM structure
                // First check for specific next page URL pattern, then check link text
                boolean hasNextPageLink = doc.select("a[href*='" + categoryPath + "/" + ((page + 1) * 20) + "']")
                        .size() > 0;

                if (!hasNextPageLink) {
                    // Check for "Další" text in links as fallback
                    hasNextPageLink = doc.select("a").stream()
                            .anyMatch(link -> {
                                String linkText = link.text().toLowerCase();
                                return linkText.contains("další") || linkText.contains("next");
                            });
                }

                hasMorePages = hasNextPageLink && !listingUrls.isEmpty() && !shouldStopEarly;

                if (!hasMorePages && !shouldStopEarly) {
                    log.info("No next page found for {} on page {} - stopping pagination", partType, page);
                }

                page++;
                Thread.sleep(1500); // Reduced from 2000ms to 1500ms for better performance
            }

            log.info("Scraped and saved {} new parts for {}", totalScraped, partType);

        } catch (Exception e) {
            log.error("Error scraping category {}: {}", partType, e.getMessage(), e);
        }
    }

    @Transactional
    private SaveResult savePartsInTransaction(List<Part> parts) {
        try {
            if (parts.isEmpty()) {
                return new SaveResult(0, 0, 0);
            }

            // Step 1: Remove duplicates within the current batch (same page)
            Map<String, Part> uniqueParts = new LinkedHashMap<>();
            for (Part part : parts) {
                uniqueParts.put(part.getUniqueHash(), part);
            }
            List<Part> dedupedParts = new ArrayList<>(uniqueParts.values());
            int intraBatchDuplicates = parts.size() - dedupedParts.size();

            if (intraBatchDuplicates > 0) {
                log.debug("Found {} duplicate parts within the same page", intraBatchDuplicates);
            }

            // Step 2: Check against database for existing parts in batches for better
            // performance
            Set<String> uniqueHashes = dedupedParts.stream()
                    .map(Part::getUniqueHash)
                    .collect(java.util.stream.Collectors.toSet());

            Set<String> existingHashes = partRepository.findExistingUniqueHashes(uniqueHashes);

            // Step 3: Filter out parts that already exist in database
            List<Part> newParts = dedupedParts.stream()
                    .filter(part -> !existingHashes.contains(part.getUniqueHash()))
                    .collect(java.util.stream.Collectors.toList());

            int databaseDuplicates = dedupedParts.size() - newParts.size();
            int actuallyInserted = 0;

            // Step 4: Insert new parts using batch processing for better performance
            if (!newParts.isEmpty()) {
                // Set timestamps for all parts before batch insert
                LocalDateTime now = LocalDateTime.now();
                for (Part part : newParts) {
                    if (part.getScrapedAt() == null) {
                        part.setScrapedAt(now);
                    }
                    if (part.getUpdatedAt() == null) {
                        part.setUpdatedAt(now);
                    }
                }

                try {
                    // Use saveAll for batch processing
                    List<Part> savedParts = partRepository.saveAll(newParts);
                    actuallyInserted = savedParts.size();
                } catch (Exception e) {
                    log.warn("Batch insert failed, falling back to individual inserts: {}", e.getMessage());
                    // Fallback to individual inserts if batch fails
                    for (Part part : newParts) {
                        try {
                            if (!partRepository.existsByUniqueHash(part.getUniqueHash())) {
                                partRepository.save(part);
                                actuallyInserted++;
                            }
                        } catch (Exception ex) {
                            log.debug("Failed to insert part (likely duplicate): {} - {}", part.getUniqueHash(),
                                    ex.getMessage());
                        }
                    }
                }
            }

            int totalDuplicates = intraBatchDuplicates + databaseDuplicates + (newParts.size() - actuallyInserted);

            if (actuallyInserted > 0 || totalDuplicates > 0) {
                log.debug("Saved {} new parts, skipped {} duplicates (intra-batch: {}, database: {}, failed: {})",
                        actuallyInserted, totalDuplicates, intraBatchDuplicates, databaseDuplicates,
                        (newParts.size() - actuallyInserted));
            }

            return new SaveResult(actuallyInserted, databaseDuplicates, intraBatchDuplicates);
        } catch (Exception e) {
            log.error("Error saving parts: {}", e.getMessage(), e);
            return new SaveResult(0, parts.size(), 0);
        }
    }

    // Helper class to track save results
    private static class SaveResult {
        final int saved;
        final int databaseDuplicates; // Parts that already existed in database
        final int intraBatchDuplicates; // Duplicates within the same page batch

        SaveResult(int saved, int databaseDuplicates, int intraBatchDuplicates) {
            this.saved = saved;
            this.databaseDuplicates = databaseDuplicates;
            this.intraBatchDuplicates = intraBatchDuplicates;
        }

        int getTotalDuplicates() {
            return databaseDuplicates + intraBatchDuplicates;
        }
    }

    public void scrapeAllCategories() {
        log.info("Starting full scraping of all PC part categories");

        // Use the correct category mappings
        for (Map.Entry<Part.PartType, String> entry : CATEGORY_MAPPINGS.entrySet()) {
            try {
                scrapeCategory(entry.getKey(), entry.getValue());
                Thread.sleep(3000); // Wait between categories
            } catch (Exception e) {
                log.error("Error scraping category {}: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("Completed full scraping of all categories");
    }

    private List<String> extractListingUrls(Document doc) {
        List<String> urls = new ArrayList<>();
        Set<String> uniqueIds = new HashSet<>(); // Track unique listing IDs
        Elements links = doc.select("a[href*=/inzerat/]");

        log.debug("Found {} links with /inzerat/ on the page", links.size());

        for (Element link : links) {
            String href = link.attr("href");
            if (href.contains("/inzerat/") && href.contains(".php")) {
                if (!href.startsWith("http")) {
                    href = BASE_URL + href;
                }

                // Extract the listing ID to avoid duplicates
                String listingId = extractExternalId(href);
                if (listingId != null && !uniqueIds.contains(listingId)) {
                    uniqueIds.add(listingId);
                    urls.add(href);
                }
            }
        }

        log.debug("Extracted {} unique listing URLs from {} total links", urls.size(), links.size());
        return urls;
    }

    private Part scrapeIndividualListing(String url, Part.PartType partType) {
        try {
            Document doc = createJsoupConnection(url).get();

            String pageText = doc.body().text();
            String externalId = extractExternalId(url);

            if (externalId == null) {
                return null;
            }

            // Extract title from the page heading or meta
            String title = extractTitle(pageText, doc);
            if (title == null || title.trim().isEmpty()) {
                return null;
            }

            // Extract price (null is allowed for "v textu" cases)
            BigDecimal price = extractPrice(pageText);
            if (price != null && price.compareTo(BigDecimal.ZERO) <= 0) {
                return null; // Reject only negative prices, allow null and positive prices
            }

            // Extract location
            String location = extractLocation(pageText);

            // Extract description
            String description = extractDescription(pageText);

            // Extract date created
            LocalDateTime dateCreated = extractDateCreated(pageText);

            // Extract seller information
            String sellerName = extractSellerName(pageText);
            String phone = extractPhone(pageText);

            // Extract view count
            Integer viewCount = extractViewCount(pageText);

            // Check if promoted
            Boolean isPromoted = pageText.contains("TOP");

            String uniqueHash = generateUniqueHash("bazos", externalId, title,
                    price != null ? price.toString() : "null");

            LocalDateTime now = LocalDateTime.now();
            return Part.builder()
                    .title(truncateString(title.trim(), 500))
                    .description(truncateString(description, 2000)) // Add truncation for description
                    .partType(partType)
                    .price(price)
                    .currency("CZK")
                    .marketplace("bazos")
                    .source("bazos") // Set source for marketplace distinction
                    .externalId(truncateString(externalId, 50))
                    .url(truncateString(url, 1000))
                    .location(truncateString(location, 200))
                    .sellerName(truncateString(sellerName, 200))
                    .phone(truncateString(phone, 100))
                    .viewCount(viewCount)
                    .isPromoted(isPromoted)
                    .uniqueHash(uniqueHash)
                    .scrapedAt(dateCreated != null ? dateCreated : now)
                    .updatedAt(now) // Explicitly set updatedAt
                    .active(true)
                    .build();

        } catch (Exception e) {
            log.warn("Error scraping individual listing {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String extractTitle(String pageText, Document doc) {
        // Try to find title in various places
        Element titleElement = doc.selectFirst("h1");
        if (titleElement != null && !titleElement.text().trim().isEmpty()) {
            return titleElement.text().trim();
        }

        // Look for the title pattern in the page text
        String[] lines = pageText.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("GeForce") || line.contains("Radeon") || line.contains("RTX") ||
                    line.contains("GTX") || line.contains("RX") || line.contains("Intel") ||
                    line.contains("AMD") || line.contains("NVIDIA")) {
                // Check if this looks like a product title
                if (line.length() > 10 && line.length() < 200 &&
                        !line.contains("Cena:") && !line.contains("Lokalita:")) {
                    return line;
                }
            }
        }

        return null;
    }

    private String extractDescription(String pageText) {
        // Look for description patterns
        int descStart = -1;
        int descEnd = -1;

        // Common description start patterns
        String[] startPatterns = { "výkonnější než", "Záruka", "Preferuji", "Prodám", "Nabízím" };
        for (String pattern : startPatterns) {
            int index = pageText.indexOf(pattern);
            if (index != -1 && (descStart == -1 || index < descStart)) {
                descStart = index;
            }
        }

        // Common description end patterns
        String[] endPatterns = { "Cena pevná", "Jméno:", "Telefon:", "Lokalita:", "©2025" };
        for (String pattern : endPatterns) {
            int index = pageText.indexOf(pattern);
            if (index != -1 && descStart != -1 && index > descStart) {
                if (descEnd == -1 || index < descEnd) {
                    descEnd = index;
                }
            }
        }

        if (descStart != -1 && descEnd != -1 && descEnd > descStart) {
            String description = pageText.substring(descStart, descEnd).trim();
            if (description.length() > 10 && description.length() < 2000) {
                return description;
            }
        }

        return null;
    }

    private String buildUrl(String categoryPath, int page) {
        if (page == 1) {
            return BASE_URL + "/" + categoryPath + "/";
        }
        return BASE_URL + "/" + categoryPath + "/" + (page * 20) + "/"; // Bazos uses 20-item increments
    }

    private String extractExternalId(String url) {
        Matcher matcher = ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private BigDecimal extractPrice(String text) {
        // First try the standard price pattern
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                String priceStr = matcher.group(1).replaceAll("\\s+", "");
                return new BigDecimal(priceStr);
            } catch (NumberFormatException e) {
                // Continue to try other patterns
            }
        }

        // Try to find price in text format (e.g., "cena: 5000 Kč", "cena v textu")
        Matcher textMatcher = PRICE_IN_TEXT_PATTERN.matcher(text);
        if (textMatcher.find()) {
            try {
                String priceStr = textMatcher.group(1).replaceAll("\\s+", "");
                return new BigDecimal(priceStr);
            } catch (NumberFormatException e) {
                // Continue to other patterns
            }
        }

        // If we find "v textu" but no price, return null to indicate undefined price
        // This allows items with negotiable/text-based pricing to be included with null
        // price
        if (text.toLowerCase().contains("v textu") || text.toLowerCase().contains("dohodou") ||
                text.toLowerCase().contains("na dotaz")) {
            log.debug("Found text-based pricing, price will be stored as null");
            return null; // Null value to indicate "price in text" - price undefined
        }

        return null;
    }

    private String extractLocation(String text) {
        Matcher matcher = LOCATION_PATTERN.matcher(text);
        if (matcher.find()) {
            return (matcher.group(1) + matcher.group(2)).trim();
        }
        return null;
    }

    private LocalDateTime extractDateCreated(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                String dateStr = matcher.group(1).trim();
                // Parse date in format "4.7. 2025"
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d.M. yyyy");
                LocalDate localDate = LocalDate.parse(dateStr, formatter);
                return localDate.atStartOfDay(); // Convert to LocalDateTime
            } catch (Exception e) {
                log.debug("Could not parse date: {}", matcher.group(1));
            }
        }
        return LocalDateTime.now();
    }

    private String extractSellerName(String pageText) {
        Pattern sellerPattern = Pattern.compile("Jméno:([^\\n]+)");
        Matcher matcher = sellerPattern.matcher(pageText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractPhone(String pageText) {
        Pattern phonePattern = Pattern.compile("Telefon:([^\\n]+)");
        Matcher matcher = phonePattern.matcher(pageText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private Integer extractViewCount(String pageText) {
        Pattern viewPattern = Pattern.compile("Vidělo:(\\d+)\\s*lidí");
        Matcher matcher = viewPattern.matcher(pageText);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String generateUniqueHash(String marketplace, String externalId, String title, String price) {
        try {
            String input = marketplace + "|" + externalId + "|" + title + "|" + price;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating hash", e);
            return marketplace + "_" + externalId + "_" + System.currentTimeMillis();
        }
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    /**
     * Create a JSoup connection with optional Tor proxy support
     */
    private org.jsoup.Connection createJsoupConnection(String url) {
        org.jsoup.Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(CONNECT_TIMEOUT)
                .followRedirects(true);

        // Add Tor proxy if enabled
        java.net.Proxy proxy = torProxyService.getTorProxy();
        if (proxy != null) {
            connection.proxy(proxy);
            log.debug("Using Tor proxy for request to: {}", url);
        }

        return connection;
    }
}
