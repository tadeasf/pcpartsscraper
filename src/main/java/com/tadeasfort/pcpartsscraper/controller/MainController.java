package com.tadeasfort.pcpartsscraper.controller;

import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.model.PartType;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import com.tadeasfort.pcpartsscraper.repository.PartTypeRepository;
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
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final PartRepository partRepository;
    private final PartTypeRepository partTypeRepository;
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

        model.addAttribute("currentPage", "dashboard");
        model.addAttribute("title", "Dashboard");
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
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer maxAgeDays,
            @RequestParam(required = false) String search,
            Model model) {

        LocalDateTime maxAge = maxAgeDays != null ? LocalDateTime.now().minusDays(maxAgeDays) : null;

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get filtered parts
        Page<Part> partsPage = partService.findWithFilters(
                partType, itemType, modelName, minPrice, maxPrice, marketplace, source, maxAge, search, pageable);

        // Get filter options
        List<Part.PartType> partTypes = Arrays.asList(Part.PartType.values());
        List<String> itemTypes = partRepository.findDistinctItemTypes();
        List<String> modelNames = itemType != null ? partRepository.findDistinctModelNamesByItemType(itemType)
                : partRepository.findDistinctModelNames();
        List<String> marketplaces = partRepository.findDistinctMarketplaces();
        List<String> sources = partRepository.findDistinctSources();

        // Add attributes to model
        model.addAttribute("currentPage", "parts");
        model.addAttribute("title", "Browse Parts");
        model.addAttribute("partsPage", partsPage);
        model.addAttribute("partTypes", partTypes);
        model.addAttribute("itemTypes", itemTypes);
        model.addAttribute("modelNames", modelNames);
        model.addAttribute("marketplaces", marketplaces);
        model.addAttribute("sources", sources);

        // Current filter values
        model.addAttribute("currentPartType", partType);
        model.addAttribute("currentItemType", itemType);
        model.addAttribute("currentModelName", modelName);
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
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer maxAgeDays,
            @RequestParam(required = false) String search,
            Model model) {

        LocalDateTime maxAge = maxAgeDays != null ? LocalDateTime.now().minusDays(maxAgeDays) : null;

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get filtered parts
        Page<Part> partsPage = partService.findWithFilters(
                partType, itemType, modelName, minPrice, maxPrice, marketplace, source, maxAge, search, pageable);

        // Get price statistics for models in the current page
        Map<String, PartType> priceStatistics = partsPage.getContent().stream()
                .filter(part -> part.getModelName() != null && part.getItemType() != null)
                .collect(Collectors.toMap(
                        part -> part.getModelName() + "_" + part.getItemType(),
                        part -> partTypeRepository.findByModelNameAndItemType(part.getModelName(), part.getItemType())
                                .orElse(null),
                        (existing, replacement) -> existing != null ? existing : replacement))
                .entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        model.addAttribute("partsPage", partsPage);
        model.addAttribute("priceStatistics", priceStatistics);
        return "fragments/parts-list";
    }

    @GetMapping("/api/models")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<String> getModelsByItemType(@RequestParam(required = false) String itemType) {
        if (itemType != null && !itemType.trim().isEmpty()) {
            return partRepository.findDistinctModelNamesByItemType(itemType);
        }
        return partRepository.findDistinctModelNames();
    }
}
