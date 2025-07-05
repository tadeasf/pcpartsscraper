package com.tadeasfort.pcpartsscraper;

import com.tadeasfort.pcpartsscraper.service.TorProxyService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootApplication
public class PCPartsScraperApplication {

	@Autowired
	private TorProxyService torProxyService;

	public static void main(String[] args) {
		SpringApplication.run(PCPartsScraperApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void initializeServices() {
		torProxyService.initializeProxies();
	}

}
