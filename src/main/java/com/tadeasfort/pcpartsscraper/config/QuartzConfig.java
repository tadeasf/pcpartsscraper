package com.tadeasfort.pcpartsscraper.config;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.service.scraping.CategoryScrapingJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@Slf4j
public class QuartzConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${app.scraping.bazos.interval-hours:3}")
    private int intervalHours;

    @Value("${app.scraping.bazos.max-concurrent-categories:5}")
    private int maxConcurrentCategories;

    @Value("${app.scraping.bazos.stagger-start:true}")
    private boolean staggerStart;

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

        // Enable automatic startup
        factory.setAutoStartup(true);

        // Wait for jobs to complete on shutdown
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setStartupDelay(10); // 10 second delay to ensure everything is initialized

        return factory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeScrapingJobs() throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean().getScheduler();

        log.info("Initializing scraping jobs with JDBC persistence...");

        // Check for existing jobs and their states
        Set<JobKey> existingJobs = scheduler.getJobKeys(GroupMatcher.jobGroupEquals("scraping"));
        log.info("Found {} existing scraping jobs in database", existingJobs.size());

        // Handle job recovery
        handleJobRecovery(scheduler);

        // Create/update jobs for each category
        createOrUpdateCategoryJobs(scheduler);

        log.info("Scraping jobs initialization completed. Concurrent execution enabled with max {} categories.",
                maxConcurrentCategories);
    }

    private void handleJobRecovery(Scheduler scheduler) throws SchedulerException {
        // Get all jobs that were executing when the scheduler was shut down
        List<JobExecutionContext> currentlyExecutingJobs = scheduler.getCurrentlyExecutingJobs();

        if (!currentlyExecutingJobs.isEmpty()) {
            log.warn("Found {} jobs that were executing during last shutdown - they will be recovered",
                    currentlyExecutingJobs.size());
        }

        // Quartz automatically handles recovery of interrupted jobs with the JDBC
        // JobStore
        // Jobs marked as "requests recovery" will be re-executed automatically
    }

    private void createOrUpdateCategoryJobs(Scheduler scheduler) throws SchedulerException {
        LocalDateTime now = LocalDateTime.now();
        int staggerIndex = 0;

        for (Map.Entry<Part.PartType, String> entry : CATEGORY_MAPPINGS.entrySet()) {
            Part.PartType partType = entry.getKey();
            String categoryPath = entry.getValue();

            String jobId = "scrapingJob_" + partType.name();
            String triggerId = "scrapingTrigger_" + partType.name();
            JobKey jobKey = new JobKey(jobId, "scraping");
            TriggerKey triggerKey = new TriggerKey(triggerId, "scraping");

            // Check if job already exists
            boolean jobExists = scheduler.checkExists(jobKey);

            if (jobExists) {
                // Check the last execution time
                Trigger existingTrigger = scheduler.getTrigger(triggerKey);
                Date lastFireTime = existingTrigger != null ? existingTrigger.getPreviousFireTime() : null;

                if (lastFireTime != null) {
                    LocalDateTime lastExecution = lastFireTime.toInstant().atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    LocalDateTime nextExpectedRun = lastExecution.plusHours(intervalHours);

                    if (now.isBefore(nextExpectedRun)) {
                        log.info("Job {} last ran at {}, next run scheduled for {}. Keeping existing schedule.",
                                partType, lastExecution, nextExpectedRun);
                        continue; // Keep existing job and schedule
                    }
                }

                log.info("Updating existing job for category: {}", partType);
            } else {
                log.info("Creating new job for category: {}", partType);
            }

            // Define the job with recovery enabled
            JobDetail categoryJob = JobBuilder.newJob(CategoryScrapingJob.class)
                    .withIdentity(jobKey)
                    .withDescription("Scrapes " + partType.getDisplayName() + " from Bazos.cz")
                    .usingJobData("partType", partType.name())
                    .usingJobData("categoryPath", categoryPath)
                    .storeDurably(true)
                    .requestRecovery(true) // Enable automatic recovery if job fails due to system crash
                    .build();

            // Calculate staggered start time for concurrent execution
            Date startTime;
            if (staggerStart && staggerIndex < maxConcurrentCategories) {
                // Stagger jobs by 30 seconds each to avoid overwhelming the target site
                int staggerSeconds = staggerIndex * 30;
                startTime = DateBuilder.futureDate(staggerSeconds, DateBuilder.IntervalUnit.SECOND);
                staggerIndex++;
            } else {
                // Start immediately for remaining jobs (they'll run when threads are available)
                startTime = new Date();
            }

            // Define the trigger
            Trigger categoryTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withDescription(
                            "Triggers " + partType.getDisplayName() + " scraping every " + intervalHours + " hours")
                    .forJob(categoryJob)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInHours(intervalHours)
                            .repeatForever()
                            .withMisfireHandlingInstructionFireNow()) // Fire immediately if missed
                    .startAt(startTime)
                    .build();

            // Schedule or reschedule the job
            if (jobExists) {
                scheduler.addJob(categoryJob, true); // Replace existing job
                scheduler.rescheduleJob(triggerKey, categoryTrigger);
            } else {
                scheduler.scheduleJob(categoryJob, categoryTrigger);
            }

            log.debug("Scheduled {} to start at {} with {} hour intervals",
                    partType, startTime, intervalHours);
        }
    }
}
