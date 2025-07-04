package com.tadeasfort.pcpartsscraper.service.scraping;

import com.tadeasfort.pcpartsscraper.model.Part;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class CategoryScrapingJob implements Job {

    @Autowired
    private Bazos bazosService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String partTypeStr = dataMap.getString("partType");
        String categoryPath = dataMap.getString("categoryPath");

        if (partTypeStr == null || categoryPath == null) {
            throw new JobExecutionException("Missing required job parameters: partType or categoryPath");
        }

        try {
            Part.PartType partType = Part.PartType.valueOf(partTypeStr);
            log.info("Starting scheduled scraping job for category: {} ({}) - Thread: {}",
                    partType, categoryPath, Thread.currentThread().getName());

            dataMap.put("lastExecutionStart", System.currentTimeMillis());

            bazosService.scrapeCategory(partType, categoryPath);

            dataMap.put("lastExecutionComplete", System.currentTimeMillis());

            log.info("Completed scheduled scraping job for category: {} successfully on thread: {}",
                    partType, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("Error during scheduled scraping for category {} on thread {}: {}",
                    partTypeStr, Thread.currentThread().getName(), e.getMessage(), e);

            dataMap.put("lastExecutionError", e.getMessage());
            dataMap.put("lastExecutionErrorTime", System.currentTimeMillis());

            throw new JobExecutionException(e);
        }
    }
}