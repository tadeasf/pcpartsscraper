package com.tadeasfort.pcpartsscraper.controller;

import com.tadeasfort.pcpartsscraper.model.BasketItem;
import com.tadeasfort.pcpartsscraper.model.PCBasket;
import com.tadeasfort.pcpartsscraper.model.Part;
import com.tadeasfort.pcpartsscraper.repository.PCBasketRepository;
import com.tadeasfort.pcpartsscraper.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/baskets")
@RequiredArgsConstructor
public class BasketController {

    private final PCBasketRepository basketRepository;
    private final PartRepository partRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public String baskets(Model model) {
        List<PCBasket> baskets = basketRepository.findAllByActiveTrueOrderByCreatedAtDesc();
        model.addAttribute("baskets", baskets);
        return "baskets";
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String basketDetails(@PathVariable Long id, Model model) {
        Optional<PCBasket> basketOpt = basketRepository.findByIdAndActiveTrue(id);
        if (basketOpt.isEmpty()) {
            return "redirect:/baskets";
        }

        model.addAttribute("basket", basketOpt.get());
        return "basket-details";
    }

    @PostMapping("/create")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> createBasket(@RequestParam String name,
            @RequestParam(required = false) String description) {
        try {
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Basket name is required");
            }

            PCBasket basket = PCBasket.builder()
                    .name(name.trim())
                    .description(description != null ? description.trim() : null)
                    .createdAt(LocalDateTime.now())
                    .active(true)
                    .build();

            basket = basketRepository.save(basket);
            return ResponseEntity.ok("basketId=" + basket.getId());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error creating basket: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/add-part")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> addPartToBasket(@PathVariable Long id,
            @RequestParam Long partId) {
        try {
            Optional<PCBasket> basketOpt = basketRepository.findByIdAndActiveTrue(id);
            if (basketOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Basket not found");
            }

            Optional<Part> partOpt = partRepository.findById(partId);
            if (partOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Part not found");
            }

            PCBasket basket = basketOpt.get();
            Part part = partOpt.get();

            // Check if part already exists in basket
            Optional<BasketItem> existingItem = basket.getItems().stream()
                    .filter(item -> item.getPart().getId().equals(partId))
                    .findFirst();

            if (existingItem.isPresent()) {
                // For marketplace listings, don't allow duplicates since each listing is unique
                return ResponseEntity.badRequest().body("This part is already in your basket");
            } else {
                // Add new item (always quantity 1 for unique marketplace listings)
                BasketItem item = BasketItem.builder()
                        .basket(basket)
                        .part(part)
                        .quantity(1)
                        .priceAtTime(part.getPrice())
                        .addedAt(LocalDateTime.now())
                        .build();
                basket.getItems().add(item);
            }

            basketRepository.save(basket);
            return ResponseEntity.ok("Part added to basket successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error adding part to basket: " + e.getMessage());
        }
    }

    @DeleteMapping("/{basketId}/items/{itemId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> removeItemFromBasket(@PathVariable Long basketId,
            @PathVariable Long itemId) {
        try {
            Optional<PCBasket> basketOpt = basketRepository.findByIdAndActiveTrue(basketId);
            if (basketOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Basket not found");
            }

            PCBasket basket = basketOpt.get();
            basket.getItems().removeIf(item -> item.getId().equals(itemId));

            basketRepository.save(basket);
            return ResponseEntity.ok("Item removed from basket successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error removing item from basket: " + e.getMessage());
        }
    }

    // Quantity updates removed - marketplace listings are unique items with
    // quantity always 1

    @DeleteMapping("/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> deleteBasket(@PathVariable Long id) {
        try {
            Optional<PCBasket> basketOpt = basketRepository.findByIdAndActiveTrue(id);
            if (basketOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Basket not found");
            }

            PCBasket basket = basketOpt.get();
            basket.setActive(false);
            basketRepository.save(basket);

            return ResponseEntity.ok("Basket deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error deleting basket: " + e.getMessage());
        }
    }

    @GetMapping("/modal/add-part/{partId}")
    @Transactional(readOnly = true)
    public String addPartModal(@PathVariable Long partId, Model model) {
        Optional<Part> partOpt = partRepository.findById(partId);
        if (partOpt.isEmpty()) {
            return "error";
        }

        List<PCBasket> baskets = basketRepository.findAllByActiveTrueOrderByCreatedAtDesc();
        model.addAttribute("part", partOpt.get());
        model.addAttribute("baskets", baskets);
        return "fragments/add-to-basket-modal";
    }

    @GetMapping("/fragment/list")
    @Transactional(readOnly = true)
    public String basketListFragment(Model model) {
        List<PCBasket> baskets = basketRepository.findAllByActiveTrueOrderByCreatedAtDesc();
        model.addAttribute("baskets", baskets);
        return "fragments/basket-list";
    }

    @GetMapping("/{id}/fragment/items")
    @Transactional(readOnly = true)
    public String basketItemsFragment(@PathVariable Long id, Model model) {
        Optional<PCBasket> basketOpt = basketRepository.findByIdAndActiveTrue(id);
        if (basketOpt.isEmpty()) {
            return "error";
        }

        model.addAttribute("basket", basketOpt.get());
        return "fragments/basket-items";
    }

    @GetMapping("/{id}/share")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<List<String>> shareBasket(@PathVariable Long id) {
        try {
            Optional<PCBasket> basketOpt = basketRepository.findByIdAndActiveTrue(id);
            if (basketOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            PCBasket basket = basketOpt.get();
            List<String> urls = basket.getItems().stream()
                    .map(item -> item.getPart().getUrl())
                    .distinct()
                    .toList();

            return ResponseEntity.ok(urls);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}