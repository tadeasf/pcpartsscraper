package com.tadeasfort.pcpartsscraper;

import com.tadeasfort.pcpartsscraper.service.TorProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class PCPartsScraperApplication {

	@Autowired
	private TorProxyService torProxyService;

	@Value("${app.scraping.enabled:true}")
	private boolean scrapingEnabled;

	public static void main(String[] args) {
		SpringApplication.run(PCPartsScraperApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void initializeServices() {
		log.info("=== PC Parts Scraper Application Started ===");
		log.info("Scraping enabled: {}", scrapingEnabled);

		// Initialize Tor proxy service
		if (torProxyService.isTorEnabled()) {
			log.info("Initializing Tor proxy service...");
			torProxyService.initializeProxies();
		}

		if (scrapingEnabled) {
			log.info("Scraping jobs are configured and will run automatically via @Scheduled annotations");
		} else {
			log.info("Scraping is disabled via configuration");
		}

		log.info("=== Application initialization completed ===");
	}
}
