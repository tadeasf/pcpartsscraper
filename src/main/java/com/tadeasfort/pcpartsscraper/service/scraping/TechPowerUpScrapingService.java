package com.tadeasfort.pcpartsscraper.service.scraping;

import com.tadeasfort.pcpartsscraper.service.TorProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TechPowerUpScrapingService {

    private final TorProxyService torProxyService;

    private static final String BASE_URL = "https://www.techpowerup.com";
    private static final String GPU_SPECS_URL = BASE_URL + "/gpu-specs/";
    private static final String CPU_SPECS_URL = BASE_URL + "/cpu-specs/";

    // Rate limiting configuration
    @Value("${app.scraping.techpowerup.base-delay-ms:2000}")
    private int baseDelayMs = 2000; // Base delay between requests

    @Value("${app.scraping.techpowerup.max-delay-ms:8000}")
    private int maxDelayMs = 8000; // Maximum delay for exponential backoff

    @Value("${app.scraping.techpowerup.max-retries:3}")
    private int maxRetries = 3; // Maximum retry attempts for 429 errors

    @Value("${app.scraping.techpowerup.timeout-ms:15000}")
    private int timeoutMs = 15000; // Request timeout

    @Value("${app.scraping.techpowerup.use-tor:true}")
    private boolean useTorProxy = true; // Whether to use Tor proxy for requests

    // CPU manufacturers and their generations
    private static final List<String> AMD_GENERATIONS = Arrays.asList("Ryzen 3", "Ryzen 5", "Ryzen 7", "Ryzen 9");
    private static final List<String> INTEL_GENERATIONS = Arrays.asList("Core i3", "Core i5", "Core i7", "Core i9",
            "Ultra 5", "Ultra 7", "Ultra 9");
    private static final List<String> GPU_MANUFACTURERS = Arrays.asList("AMD", "NVIDIA", "Intel");

    /**
     * Fetch document with retry logic and rate limiting
     */
    private Document fetchDocumentWithRetry(String url) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {} for URL: {}", attempt, url);
                    exponentialBackoffDelay(attempt - 1);
                }

                return createConnection(url).get();

            } catch (HttpStatusException e) {
                lastException = e;

                if (e.getStatusCode() == 429) {
                    log.warn("Rate limited (429) on attempt {} for URL: {}. Will retry after backoff.",
                            attempt + 1, url);

                    if (attempt == maxRetries) {
                        log.error("Max retries exceeded for URL: {}. Final attempt failed with 429.", url);
                        break;
                    }

                    // For 429 errors, use longer backoff and potentially switch to Tor
                    if (useTorProxy && attempt >= 1) {
                        log.info("Switching to Tor proxy due to rate limiting");
                    }
                    continue;

                } else if (e.getStatusCode() >= 500) {
                    log.warn("Server error {} on attempt {} for URL: {}. Will retry.",
                            e.getStatusCode(), attempt + 1, url);
                    continue;

                } else {
                    // Client errors other than 429 - don't retry
                    log.error("Client error {} for URL: {}. Not retrying.", e.getStatusCode(), url);
                    throw e;
                }

            } catch (IOException e) {
                lastException = e;
                log.warn("Network error on attempt {} for URL: {}. Error: {}",
                        attempt + 1, url, e.getMessage());

                if (attempt == maxRetries) {
                    break;
                }
            }
        }

        throw new IOException("Failed to fetch URL after " + (maxRetries + 1) + " attempts: " + url, lastException);
    }

    /**
     * Create JSoup connection with proper configuration and optional Tor proxy
     */
    private org.jsoup.Connection createConnection(String url) {
        org.jsoup.Connection connection = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(timeoutMs)
                .followRedirects(true)
                .ignoreHttpErrors(false) // We want to handle HTTP errors explicitly
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1");

        // Add Tor proxy if enabled
        if (useTorProxy) {
            Proxy proxy = torProxyService.getTorProxy();
            if (proxy != null) {
                connection.proxy(proxy);
                log.debug("Using Tor proxy for TechPowerUp request: {}", url);
            } else {
                log.warn("Tor proxy requested but not available, using direct connection");
            }
        }

        return connection;
    }

    public TechPowerUp.ScrapingResult scrapeGPUsByYear(int year) {
        log.info("Scraping GPUs for year: {}", year);
        List<TechPowerUp.ComponentInfo> allGpus = new ArrayList<>();

        try {
            // Scrape GPUs by manufacturer to stay under 100 results limit
            for (String manufacturer : GPU_MANUFACTURERS) {
                delayRequest(); // Be respectful to the server

                String url = GPU_SPECS_URL + "?mfgr=" + manufacturer + "&released=" + year + "&sort=name";
                Document doc = fetchDocumentWithRetry(url);

                List<TechPowerUp.ComponentInfo> gpus = parseGPUTable(doc);
                allGpus.addAll(gpus);

                log.debug("Scraped {} GPUs for manufacturer {} in year {}", gpus.size(), manufacturer, year);
            }

            return TechPowerUp.ScrapingResult.builder()
                    .componentType("gpu")
                    .year(year)
                    .totalComponents(allGpus.size())
                    .components(allGpus)
                    .successful(true)
                    .scrapedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error scraping GPUs for year {}: {}", year, e.getMessage());
            return TechPowerUp.ScrapingResult.builder()
                    .componentType("gpu")
                    .year(year)
                    .totalComponents(0)
                    .components(new ArrayList<>())
                    .successful(false)
                    .errorMessage(e.getMessage())
                    .scrapedAt(LocalDateTime.now())
                    .build();
        }
    }

    public TechPowerUp.ScrapingResult scrapeCPUsByYear(int year) {
        log.info("Scraping CPUs for year: {}", year);
        List<TechPowerUp.ComponentInfo> allCpus = new ArrayList<>();

        try {
            // Scrape AMD CPUs by generation
            for (String generation : AMD_GENERATIONS) {
                delayRequest(); // Be respectful to the server

                String url = CPU_SPECS_URL + "?f=year_" + year + "~mfgr_AMD~generation_AMD+"
                        + generation.replace(" ", "+");
                Document doc = fetchDocumentWithRetry(url);

                List<TechPowerUp.ComponentInfo> cpus = parseCPUTable(doc);
                allCpus.addAll(cpus);

                log.debug("Scraped {} CPUs for AMD {} in year {}", cpus.size(), generation, year);
            }

            // Scrape Intel CPUs by generation
            for (String generation : INTEL_GENERATIONS) {
                delayRequest(); // Be respectful to the server

                String url = CPU_SPECS_URL + "?f=year_" + year + "~mfgr_Intel~generation_Intel+"
                        + generation.replace(" ", "+");
                Document doc = fetchDocumentWithRetry(url);

                List<TechPowerUp.ComponentInfo> cpus = parseCPUTable(doc);
                allCpus.addAll(cpus);

                log.debug("Scraped {} CPUs for Intel {} in year {}", cpus.size(), generation, year);
            }

            return TechPowerUp.ScrapingResult.builder()
                    .componentType("cpu")
                    .year(year)
                    .totalComponents(allCpus.size())
                    .components(allCpus)
                    .successful(true)
                    .scrapedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error scraping CPUs for year {}: {}", year, e.getMessage());
            return TechPowerUp.ScrapingResult.builder()
                    .componentType("cpu")
                    .year(year)
                    .totalComponents(0)
                    .components(new ArrayList<>())
                    .successful(false)
                    .errorMessage(e.getMessage())
                    .scrapedAt(LocalDateTime.now())
                    .build();
        }
    }

    private List<TechPowerUp.ComponentInfo> parseGPUTable(Document doc) {
        List<TechPowerUp.ComponentInfo> gpus = new ArrayList<>();

        // Find the table with GPU specifications
        Elements tableRows = doc.select("table.processors tr");

        for (Element row : tableRows) {
            Elements cells = row.select("td");
            if (cells.size() >= 8) { // Ensure we have enough columns for GPU table
                try {
                    String name = cells.get(0).text().trim();
                    String gpuChip = cells.get(1).text().trim();
                    String releaseDate = cells.get(2).text().trim();
                    String bus = cells.get(3).text().trim();
                    String memory = cells.get(4).text().trim();
                    String gpuClock = cells.get(5).text().trim();
                    String memoryClock = cells.get(6).text().trim();
                    String shaders = cells.get(7).text().trim();

                    // Skip header rows
                    if (name.equals("Product Name") || name.isEmpty())
                        continue;

                    TechPowerUp.ComponentInfo gpu = TechPowerUp.ComponentInfo.builder()
                            .name(name)
                            .codename(gpuChip)
                            .itemType("GPU")
                            .brand(extractBrand(name))
                            .series(extractGPUSeries(name))
                            .memory(memory)
                            .busWidth(bus)
                            .specifications(String.format("GPU Clock: %s, Memory Clock: %s, Shaders: %s", gpuClock,
                                    memoryClock, shaders))
                            .releaseDate(parseReleaseDate(releaseDate))
                            .releaseYear(extractYear(releaseDate))
                            .techPowerUpUrl(extractDetailUrl(cells.get(0)))
                            .build();

                    gpus.add(gpu);

                } catch (Exception e) {
                    log.warn("Error parsing GPU row: {}", e.getMessage());
                }
            }
        }

        return gpus;
    }

    private List<TechPowerUp.ComponentInfo> parseCPUTable(Document doc) {
        List<TechPowerUp.ComponentInfo> cpus = new ArrayList<>();

        // Find the table with CPU specifications - correct selector for CPU table
        Elements tableRows = doc.select("table.items-desktop-table tr");

        for (Element row : tableRows) {
            Elements cells = row.select("td");
            if (cells.size() >= 9) { // Ensure we have enough columns for CPU table
                try {
                    String name = cells.get(0).text().trim();
                    String codename = cells.get(1).text().trim();
                    String cores = cells.get(2).text().trim();
                    String clock = cells.get(3).text().trim();
                    String socket = cells.get(4).text().trim();
                    String process = cells.get(5).text().trim();
                    String l3Cache = cells.get(6).text().trim();
                    String tdp = cells.get(7).text().trim();
                    String released = cells.get(8).text().trim();

                    // Skip header rows
                    if (name.equals("Name") || name.isEmpty())
                        continue;

                    TechPowerUp.ComponentInfo cpu = TechPowerUp.ComponentInfo.builder()
                            .name(name)
                            .codename(codename)
                            .itemType("CPU")
                            .brand(extractBrand(name))
                            .series(extractCPUSeries(name))
                            .socket(socket)
                            .baseClockSpeed(clock)
                            .cores(extractCoresFromString(cores))
                            .threads(extractThreadsFromString(cores))
                            .process(process)
                            .cache(l3Cache)
                            .tdp(tdp)
                            .releaseDate(parseReleaseDate(released))
                            .releaseYear(extractYear(released))
                            .techPowerUpUrl(extractDetailUrl(cells.get(0)))
                            .build();

                    cpus.add(cpu);

                } catch (Exception e) {
                    log.warn("Error parsing CPU row: {}", e.getMessage());
                }
            }
        }

        return cpus;
    }

    private String extractBrand(String name) {
        if (name.toLowerCase().contains("geforce") || name.toLowerCase().contains("rtx") ||
                name.toLowerCase().contains("gtx") || name.toLowerCase().contains("nvidia")) {
            return "NVIDIA";
        } else if (name.toLowerCase().contains("radeon") || name.toLowerCase().contains("amd") ||
                name.toLowerCase().contains("ryzen") || name.toLowerCase().contains("athlon")) {
            return "AMD";
        } else if (name.toLowerCase().contains("intel") || name.toLowerCase().contains("core") ||
                name.toLowerCase().contains("pentium") || name.toLowerCase().contains("celeron") ||
                name.toLowerCase().contains("xeon") || name.toLowerCase().contains("atom")) {
            return "Intel";
        }
        return "Unknown";
    }

    private String extractGPUSeries(String name) {
        // NVIDIA RTX/GTX series
        Pattern pattern = Pattern.compile("(RTX|GTX)\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase() + " " + matcher.group(2) + "0";
        }

        // AMD RX series
        pattern = Pattern.compile("RX\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(name);
        if (matcher.find()) {
            return "RX " + matcher.group(1) + "000";
        }

        return "Unknown";
    }

    private String extractCPUSeries(String name) {
        // AMD Ryzen series
        Pattern pattern = Pattern.compile("(Ryzen\\s*\\d)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Intel Core series
        pattern = Pattern.compile("(Core\\s*i\\d)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Intel Ultra series
        pattern = Pattern.compile("(Ultra\\s*\\d)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "Unknown";
    }

    private Integer extractCoresFromString(String coresStr) {
        // Extract cores from strings like "4c", "8c/16t", "12c/24t", etc.
        Pattern pattern = Pattern.compile("(\\d+)c");
        Matcher matcher = pattern.matcher(coresStr);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        // Also handle formats like "4 " (just number)
        pattern = Pattern.compile("^(\\d+)\\s*$");
        matcher = pattern.matcher(coresStr);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    private Integer extractThreadsFromString(String coresStr) {
        // Extract threads from strings like "4c/8t", "8c/16t", etc.
        Pattern pattern = Pattern.compile("(\\d+)c/(\\d+)t");
        Matcher matcher = pattern.matcher(coresStr);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }

        // If no threads specified, assume same as cores
        Integer cores = extractCoresFromString(coresStr);
        return cores;
    }

    private LocalDateTime parseReleaseDate(String dateStr) {
        try {
            // TechPowerUp uses formats like "Jan 2025", "Q1 2025", etc.
            // For now, just extract year and set to January 1st
            Integer year = extractYear(dateStr);
            if (year != null) {
                return LocalDateTime.of(year, 1, 1, 0, 0);
            }
        } catch (Exception e) {
            log.debug("Could not parse release date: {}", dateStr);
        }
        return null;
    }

    private Integer extractYear(String dateStr) {
        Pattern pattern = Pattern.compile("(\\d{4})");
        Matcher matcher = pattern.matcher(dateStr);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String extractDetailUrl(Element cell) {
        Element link = cell.select("a").first();
        if (link != null) {
            String href = link.attr("href");
            if (href.startsWith("/")) {
                return BASE_URL + href;
            }
            return href;
        }
        return null;
    }

    /**
     * Intelligent delay with randomization to avoid predictable patterns
     */
    private void delayRequest() {
        delayRequest(baseDelayMs);
    }

    /**
     * Delay with custom duration and randomization
     */
    private void delayRequest(int delayMs) {
        try {
            // Add 20% randomization to avoid predictable patterns
            int randomizedDelay = delayMs + ThreadLocalRandom.current().nextInt(0, delayMs / 5);
            log.debug("Delaying request for {}ms", randomizedDelay);
            Thread.sleep(randomizedDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Request delay interrupted");
        }
    }

    /**
     * Exponential backoff delay for retry scenarios
     */
    private void exponentialBackoffDelay(int attempt) {
        int delay = Math.min(baseDelayMs * (int) Math.pow(2, attempt), maxDelayMs);
        delayRequest(delay);
    }

    /**
     * Public method for external classes to request delays (e.g.,
     * PartTypeScrapingJob)
     */
    public void delayRequestPublic() {
        delayRequest();
    }
}
