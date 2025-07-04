package com.tadeasfort.pcpartsscraper.config;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.service.scraping.CategoryScrapingJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class QuartzConfig {

    @Autowired
    private ApplicationContext applicationContext;

    // Category mappings - same as in Bazos service
    private static final Map<Part.PartType, String> CATEGORY_MAPPINGS = new HashMap<>();
    static {
        CATEGORY_MAPPINGS.put(Part.PartType.GPU, "graficka");
        CATEGORY_MAPPINGS.put(Part.PartType.STORAGE_HDD, "hdd");
        CATEGORY_MAPPINGS.put(Part.PartType.CONSOLE, "playstation");
        CATEGORY_MAPPINGS.put(Part.PartType.COOLING, "chladic");
        CATEGORY_MAPPINGS.put(Part.PartType.KEYBOARD, "klavesnice");
        CATEGORY_MAPPINGS.put(Part.PartType.MONITOR, "monitor");
        CATEGORY_MAPPINGS.put(Part.PartType.MODEM, "modem");
        CATEGORY_MAPPINGS.put(Part.PartType.LAPTOP, "notebook");
        CATEGORY_MAPPINGS.put(Part.PartType.RAM, "pamet");
        CATEGORY_MAPPINGS.put(Part.PartType.OTHER, "pc");
        CATEGORY_MAPPINGS.put(Part.PartType.CPU, "procesor");
        CATEGORY_MAPPINGS.put(Part.PartType.NETWORKING, "sit");
        CATEGORY_MAPPINGS.put(Part.PartType.SCANNER, "scaner");
        CATEGORY_MAPPINGS.put(Part.PartType.CASE, "case");
        CATEGORY_MAPPINGS.put(Part.PartType.TABLET, "tablet");
        CATEGORY_MAPPINGS.put(Part.PartType.WIFI, "wifi");
        CATEGORY_MAPPINGS.put(Part.PartType.MOTHERBOARD, "motherboard");
        CATEGORY_MAPPINGS.put(Part.PartType.AUDIO_CARD, "sound");
    }

    @Bean
    public SpringBeanJobFactory springBeanJobFactory() {
        SpringBeanJobFactory jobFactory = new SpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(springBeanJobFactory());
        return factory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeScrapingJobs() throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean().getScheduler();

        // Create individual jobs for each category
        for (Map.Entry<Part.PartType, String> entry : CATEGORY_MAPPINGS.entrySet()) {
            Part.PartType partType = entry.getKey();
            String categoryPath = entry.getValue();

            String jobId = "scrapingJob_" + partType.name();
            String triggerId = "scrapingTrigger_" + partType.name();

            // Define the job with category-specific data
            JobDetail categoryJob = JobBuilder.newJob(CategoryScrapingJob.class)
                    .withIdentity(jobId, "scraping")
                    .withDescription("Scrapes " + partType.getDisplayName() + " from Bazos.cz")
                    .usingJobData("partType", partType.name())
                    .usingJobData("categoryPath", categoryPath)
                    .storeDurably()
                    .build();

            // Define the trigger - runs every 3 hours with staggered start times
            // to avoid overwhelming the target site
            int staggerMinutes = CATEGORY_MAPPINGS.size() > 1
                    ? (int) ((CATEGORY_MAPPINGS.keySet().stream().toList().indexOf(partType) * 180.0)
                            / CATEGORY_MAPPINGS.size())
                    : 0;

            Trigger categoryTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerId, "scraping")
                    .withDescription("Triggers " + partType.getDisplayName() + " scraping every 3 hours")
                    .forJob(categoryJob)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInHours(3)
                            .repeatForever())
                    .startAt(DateBuilder.futureDate(staggerMinutes, DateBuilder.IntervalUnit.MINUTE))
                    .build();

            // Schedule the job if it doesn't exist
            if (!scheduler.checkExists(categoryJob.getKey())) {
                scheduler.scheduleJob(categoryJob, categoryTrigger);
            }
        }
    }
}
