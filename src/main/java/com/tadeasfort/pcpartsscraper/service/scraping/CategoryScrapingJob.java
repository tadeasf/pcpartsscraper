package com.tadeasfort.pcpartsscraper.service.scraping;

import com.tadeasfort.pcpartsscraper.model.Part;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
            log.info("Starting scheduled scraping job for category: {} ({})", partType, categoryPath);

            bazosService.scrapeCategory(partType, categoryPath);

            log.info("Completed scheduled scraping job for category: {} successfully", partType);
        } catch (Exception e) {
            log.error("Error during scheduled scraping for category {}: {}", partTypeStr, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}