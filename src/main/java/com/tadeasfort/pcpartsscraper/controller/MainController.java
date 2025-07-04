package com.tadeasfort.pcpartsscraper.controller;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final PartRepository partRepository;

    @GetMapping("/")
    public String index(Model model) {
        // Get statistics for dashboard
        long totalParts = partRepository.count();
        long recentParts = partRepository.countNewPartsScrapedSince(LocalDateTime.now().minusDays(1));

        List<Object[]> partCountsByType = partRepository.getPartCountsByType();
        List<Object[]> partCountsByMarketplace = partRepository.getPartCountsByMarketplace();

        // Get latest parts
        Pageable latestPageable = PageRequest.of(0, 10);
        List<Part> latestParts = partRepository.findLatestParts(latestPageable);

        model.addAttribute("totalParts", totalParts);
        model.addAttribute("recentParts", recentParts);
        model.addAttribute("partCountsByType", partCountsByType);
        model.addAttribute("partCountsByMarketplace", partCountsByMarketplace);
        model.addAttribute("latestParts", latestParts);
        model.addAttribute("partTypes", Arrays.asList(Part.PartType.values()));

        return "index";
    }

    @GetMapping("/parts")
    public String parts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "scrapedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Part.PartType partType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String search,
            Model model) {

        Sort sort = Sort.by(sortDir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Part> partsPage = partRepository.findWithFilters(
                partType, minPrice, maxPrice, marketplace, search, pageable);

        model.addAttribute("partsPage", partsPage);
        model.addAttribute("partTypes", Arrays.asList(Part.PartType.values()));
        model.addAttribute("currentPartType", partType);
        model.addAttribute("currentMinPrice", minPrice);
        model.addAttribute("currentMaxPrice", maxPrice);
        model.addAttribute("currentMarketplace", marketplace);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSort", sortBy);
        model.addAttribute("currentSortDir", sortDir);

        return "parts";
    }

    @GetMapping("/parts/fragment")
    public String partsFragment(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "scrapedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Part.PartType partType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String search,
            Model model) {

        Sort sort = Sort.by(sortDir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Part> partsPage = partRepository.findWithFilters(
                partType, minPrice, maxPrice, marketplace, search, pageable);

        model.addAttribute("partsPage", partsPage);

        return "fragments/parts-list";
    }
}
