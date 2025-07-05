package com.tadeasfort.pcpartsscraper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.scraping.enabled", havingValue = "true", matchIfMissing = true)
public class QuartzConfig {

    /**
     * Spring-aware job factory for dependency injection
     * This is all we need - Spring Boot will handle the rest automatically
     */
    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
        SpringBeanJobFactory jobFactory = new SpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }
}
