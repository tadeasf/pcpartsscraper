package com.tadeasfort.pcpartsscraper.controller;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import com.tadeasfort.pcpartsscraper.service.PartService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final PartRepository partRepository;
    private final PartService partService;

    @GetMapping("/")
    public String index(Model model) {
        // Get some basic stats for the homepage
        long totalParts = partRepository.countByActiveTrue();
        List<String> sources = partRepository.findDistinctSources();
        List<String> marketplaces = partRepository.findDistinctMarketplaces();

        // Get recent parts count
        long recentParts = partRepository.countNewPartsScrapedSince(LocalDateTime.now().minusDays(1));

        // Get part counts by type and marketplace
        List<Object[]> partCountsByType = partRepository.getPartCountsByType();
        List<Object[]> partCountsByMarketplace = partRepository.getPartCountsByMarketplace();

        // Get latest parts
        List<Part> latestParts = partRepository.findLatestParts(PageRequest.of(0, 5));

        model.addAttribute("totalParts", totalParts);
        model.addAttribute("sources", sources);
        model.addAttribute("marketplaces", marketplaces);
        model.addAttribute("partTypes", Part.PartType.values());
        model.addAttribute("recentParts", recentParts);
        model.addAttribute("partCountsByType", partCountsByType);
        model.addAttribute("partCountsByMarketplace", partCountsByMarketplace);
        model.addAttribute("latestParts", latestParts);

        return "index";
    }

    @GetMapping("/parts")
    @Transactional(readOnly = true)
    public String parts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "scrapedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Part.PartType partType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer maxAgeDays,
            @RequestParam(required = false) String search,
            Model model) {

        // Convert empty strings to null for proper filtering
        String searchParam = (search != null && search.trim().isEmpty()) ? null : search;
        String marketplaceParam = (marketplace != null && marketplace.trim().isEmpty()) ? null : marketplace;
        String sourceParam = (source != null && source.trim().isEmpty()) ? null : source;

        // Calculate maxAge from maxAgeDays
        LocalDateTime maxAge = null;
        if (maxAgeDays != null && maxAgeDays > 0) {
            maxAge = LocalDateTime.now().minusDays(maxAgeDays);
        }

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get filtered parts using the service
        Page<Part> partsPage = partService.findWithFilters(
                partType, minPrice, maxPrice, marketplaceParam, sourceParam, maxAge, searchParam, pageable);

        // Get filter options
        List<String> sources = partRepository.findDistinctSources();
        List<String> marketplaces = partRepository.findDistinctMarketplaces();

        model.addAttribute("partsPage", partsPage);
        model.addAttribute("sources", sources);
        model.addAttribute("marketplaces", marketplaces);
        model.addAttribute("partTypes", Part.PartType.values());

        // Add current filter values to model
        model.addAttribute("currentPartType", partType);
        model.addAttribute("currentMinPrice", minPrice);
        model.addAttribute("currentMaxPrice", maxPrice);
        model.addAttribute("currentMarketplace", marketplace);
        model.addAttribute("currentSource", source);
        model.addAttribute("currentMaxAgeDays", maxAgeDays);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortBy", sortBy);
        model.addAttribute("currentSortDir", sortDir);

        return "parts";
    }

    @GetMapping("/parts/fragment")
    @Transactional(readOnly = true)
    public String partsFragment(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "scrapedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Part.PartType partType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer maxAgeDays,
            @RequestParam(required = false) String search,
            Model model) {

        // Convert empty strings to null for proper filtering
        String searchParam = (search != null && search.trim().isEmpty()) ? null : search;
        String marketplaceParam = (marketplace != null && marketplace.trim().isEmpty()) ? null : marketplace;
        String sourceParam = (source != null && source.trim().isEmpty()) ? null : source;

        // Calculate maxAge from maxAgeDays
        LocalDateTime maxAge = null;
        if (maxAgeDays != null && maxAgeDays > 0) {
            maxAge = LocalDateTime.now().minusDays(maxAgeDays);
        }

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get filtered parts using the service
        Page<Part> partsPage = partService.findWithFilters(
                partType, minPrice, maxPrice, marketplaceParam, sourceParam, maxAge, searchParam, pageable);

        model.addAttribute("partsPage", partsPage);
        return "fragments/parts-list";
    }
}
