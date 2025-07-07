package com.tadeasfort.pcpartsscraper.service.scraping;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.model.JobStatus;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import com.tadeasfort.pcpartsscraper.repository.JobStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tadeasfort.pcpartsscraper.service.ComponentExtractionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartTypeScrapingJob {

    private final TechPowerUpScrapingService techPowerUpService;
    private final PartRepository partRepository;
    private final ComponentExtractionService componentExtractionService;
    private final JobStatusRepository jobStatusRepository;
    private final BazosScrapingService bazosScrapingService;

    private static final String TECHPOWERUP_JOB_NAME = "techpowerup_initial_scraping";

    /**
     * Main scheduled job that runs the entire workflow every 3 hours
     * This replaces all the complex Quartz scheduling
     */
    @Scheduled(fixedRate = 3 * 60 * 60 * 1000, initialDelay = 30 * 1000) // 3 hours, 30 second initial delay
    public void runCompleteScrapingWorkflow() {
        log.info("=== STARTING Complete Scraping Workflow ===");

        try {
            // Step 1: Run TechPowerUp initialization if not completed
            if (!isTechPowerUpScrapingCompleted()) {
                log.info("Running TechPowerUp initialization...");
                runInitialTechPowerUpScraping();

                // If still not completed, skip the rest for this run
                if (!isTechPowerUpScrapingCompleted()) {
                    log.info("TechPowerUp initialization not completed, skipping other tasks");
                    return;
                }
            }

            // Step 2: Run Bazos scraping
            log.info("Running Bazos scraping...");
            bazosScrapingService.scrapeAllCategories();

            // Step 3: Process new parts and update components
            log.info("Processing new parts...");
            processNewParts();

            // Step 4: Calculate price statistics
            log.info("Calculating price statistics...");
            calculatePriceStatistics();

            // Step 5: Update component database
            log.info("Updating component database...");
            updateComponentDatabase();

            log.info("=== COMPLETED Complete Scraping Workflow ===");

        } catch (Exception e) {
            log.error("ERROR during complete scraping workflow: {}", e.getMessage(), e);
        }
    }

    // Method called by Quartz job - no @Scheduled annotation to avoid conflicts
    public void updateComponentDatabase() {
        log.info("Starting component database update job");

        try {
            // Check if initial TechPowerUp scraping has been completed - separate
            // transaction
            if (!isTechPowerUpScrapingCompleted()) {
                log.info("Initial TechPowerUp scraping not completed, running it now");
                runInitialTechPowerUpScraping();
                return; // Exit early, let the initial scraping complete
            }

            // Regular update - scrape only current year (HTTP operations outside
            // transaction)
            int currentYear = LocalDateTime.now().getYear();
            scrapeAndUpdateComponentsForYear(currentYear);

            log.info("Component database update job completed successfully");
        } catch (Exception e) {
            log.error("Error in component database update job: {}", e.getMessage(), e);
        }
    }

    // Method called by Quartz job - no @Scheduled annotation to avoid conflicts
    public void processNewParts() {
        log.info("Starting new parts processing job");

        try {
            // Find parts that don't have component type extracted yet - separate
            // transaction
            List<Part> unprocessedParts = findUnprocessedParts();

            log.info("Found {} unprocessed parts", unprocessedParts.size());

            int processed = 0;
            int batchSize = 50; // Process in smaller batches to avoid long transactions

            for (int i = 0; i < unprocessedParts.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, unprocessedParts.size());
                List<Part> batch = unprocessedParts.subList(i, endIndex);

                processed += processBatchOfParts(batch);

                if (processed % 100 == 0) {
                    log.info("Processed {} parts", processed);
                }
            }

            log.info("New parts processing job completed. Processed {} parts", processed);
        } catch (Exception e) {
            log.error("Error in new parts processing job: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    private List<Part> findUnprocessedParts() {
        return partRepository.findByItemTypeIsNull();
    }

    @Transactional
    private int processBatchOfParts(List<Part> parts) {
        int processed = 0;
        for (Part part : parts) {
            try {
                componentExtractionService.extractAndUpdatePartInfo(part);
                processed++;
            } catch (Exception e) {
                log.warn("Error processing part {}: {}", part.getId(), e.getMessage());
            }
        }
        return processed;
    }

    // Manual trigger for specific year
    public void scrapeComponentsForYear(int year) {
        log.info("Manually triggered component scraping for year: {}", year);
        scrapeAndUpdateComponentsForYear(year);
    }

    private void scrapeAndUpdateComponentsForYear(int year) {
        try {
            // Scrape GPUs
            TechPowerUp.ScrapingResult gpuResult = techPowerUpService.scrapeGPUsByYear(year);
            if (gpuResult.isSuccessful()) {
                log.info("Successfully scraped {} GPUs for year {}", gpuResult.getTotalComponents(), year);
                processComponentData(gpuResult.getComponents());
            } else {
                log.warn("Failed to scrape GPUs for year {}: {}", year, gpuResult.getErrorMessage());
            }

            techPowerUpService.delayRequestPublic();

            // Scrape CPUs
            TechPowerUp.ScrapingResult cpuResult = techPowerUpService.scrapeCPUsByYear(year);
            if (cpuResult.isSuccessful()) {
                log.info("Successfully scraped {} CPUs for year {}", cpuResult.getTotalComponents(), year);
                processComponentData(cpuResult.getComponents());
            } else {
                log.warn("Failed to scrape CPUs for year {}: {}", year, cpuResult.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Error scraping components for year {}: {}", year, e.getMessage());
        }
    }

    private void processComponentData(List<TechPowerUp.ComponentInfo> components) {
        for (TechPowerUp.ComponentInfo component : components) {
            try {
                // Store component info for later use in extraction
                componentExtractionService.addKnownComponent(component);
            } catch (Exception e) {
                log.warn("Error processing component {}: {}", component.getName(), e.getMessage());
            }
        }
    }

    // Method called by Quartz job - no @Scheduled annotation to avoid conflicts
    public void calculatePriceStatistics() {
        log.info("Starting price statistics calculation");

        try {
            // Get all parts with extracted component info - separate read-only transaction
            List<Part> partsWithTypes = getPartsWithTypes();

            // Group by model name - this happens outside of transaction
            Map<String, List<Part>> partsByModel = partsWithTypes.stream()
                    .filter(part -> part.getModelName() != null && part.getPrice() != null)
                    .collect(Collectors.groupingBy(Part::getModelName));

            log.info("Calculating statistics for {} different models", partsByModel.size());

            for (Map.Entry<String, List<Part>> entry : partsByModel.entrySet()) {
                String modelName = entry.getKey();
                List<Part> parts = entry.getValue();

                try {
                    calculateAndStoreStatistics(modelName, parts);
                } catch (Exception e) {
                    log.warn("Error calculating statistics for model {}: {}", modelName, e.getMessage());
                }
            }

            log.info("Price statistics calculation completed");
        } catch (Exception e) {
            log.error("Error in price statistics calculation: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    private List<Part> getPartsWithTypes() {
        return partRepository.findByItemTypeIsNotNull();
    }

    private void calculateAndStoreStatistics(String modelName, List<Part> parts) {
        if (parts.isEmpty())
            return;

        List<BigDecimal> prices = parts.stream()
                .map(Part::getPrice)
                .filter(price -> price != null && price.compareTo(BigDecimal.ZERO) > 0)
                .sorted()
                .collect(Collectors.toList());

        if (prices.isEmpty())
            return;

        BigDecimal minPrice = prices.get(0);
        BigDecimal maxPrice = prices.get(prices.size() - 1);

        // Calculate average
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averagePrice = sum.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);

        // Calculate median
        BigDecimal medianPrice;
        int size = prices.size();
        if (size % 2 == 0) {
            medianPrice = prices.get(size / 2 - 1).add(prices.get(size / 2))
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else {
            medianPrice = prices.get(size / 2);
        }

        // Get sample part for metadata
        Part samplePart = parts.get(0);

        // Store or update statistics
        componentExtractionService.updatePriceStatistics(
                modelName,
                samplePart.getItemType(),
                samplePart.getCategory(),
                averagePrice,
                medianPrice,
                minPrice,
                maxPrice,
                parts.size(),
                (int) parts.stream().filter(p -> p.getActive()).count());

        log.debug("Updated statistics for {}: avg={}, median={}, min={}, max={}, count={}",
                modelName, averagePrice, medianPrice, minPrice, maxPrice, parts.size());
    }

    @Transactional(readOnly = true)
    public boolean isTechPowerUpScrapingCompleted() {
        return jobStatusRepository.existsByJobNameAndCompletedTrueAndSuccessfulTrue(TECHPOWERUP_JOB_NAME);
    }

    public void runInitialTechPowerUpScraping() {
        log.info("Starting initial TechPowerUp scraping");

        // Initialize job status in a separate transaction
        JobStatus jobStatus = initializeJobStatus();

        try {
            // Scrape last 15 years for initial population (NO @Transactional - HTTP
            // operations)
            int currentYear = LocalDateTime.now().getYear();
            int successfulYears = 0;

            for (int year = currentYear - 14; year <= currentYear; year++) {
                try {
                    scrapeAndUpdateComponentsForYear(year);
                    successfulYears++;
                    techPowerUpService.delayRequestPublic(); // Be respectful to the server
                } catch (Exception e) {
                    log.warn("Failed to scrape year {}: {}", year, e.getMessage());
                }
            }

            // Update job status in a separate transaction
            updateJobStatusOnCompletion(jobStatus, successfulYears);

        } catch (Exception e) {
            log.error("Error in initial TechPowerUp scraping: {}", e.getMessage(), e);
            updateJobStatusOnError(jobStatus, e.getMessage());
        }
    }

    @Transactional
    private JobStatus initializeJobStatus() {
        JobStatus jobStatus = jobStatusRepository.findByJobName(TECHPOWERUP_JOB_NAME)
                .orElse(JobStatus.builder()
                        .jobName(TECHPOWERUP_JOB_NAME)
                        .build());

        jobStatus.setLastAttemptAt(LocalDateTime.now());
        jobStatus.setAttemptCount(jobStatus.getAttemptCount() != null ? jobStatus.getAttemptCount() + 1 : 1);
        return jobStatusRepository.save(jobStatus);
    }

    @Transactional
    private void updateJobStatusOnCompletion(JobStatus jobStatus, int successfulYears) {
        // Mark as completed if we successfully scraped at least 3 years
        if (successfulYears >= 3) {
            jobStatus.setCompleted(true);
            jobStatus.setSuccessful(true);
            jobStatus.setCompletedAt(LocalDateTime.now());
            jobStatus.setLastRunAt(LocalDateTime.now());
            jobStatus.setLastError(null);

            log.info("Initial TechPowerUp scraping completed successfully. Scraped {} years", successfulYears);
        } else {
            jobStatus.setLastError("Only scraped " + successfulYears + " years, minimum 3 required");
            log.warn("Initial TechPowerUp scraping incomplete. Only scraped {} years", successfulYears);
        }

        jobStatusRepository.save(jobStatus);
    }

    @Transactional
    private void updateJobStatusOnError(JobStatus jobStatus, String errorMessage) {
        jobStatus.setLastError(errorMessage);
        jobStatusRepository.save(jobStatus);
    }
}
