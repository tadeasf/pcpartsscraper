package com.tadeasfort.pcpartsscraper.service;

import com.tadeasfort.pcpartsscraper.model.Part;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartService {

    private final EntityManager entityManager;

    public Page<Part> findWithFilters(
            Part.PartType partType,
            String itemType,
            String modelName,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String marketplace,
            String source,
            LocalDateTime maxAge,
            String search,
            Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Part> query = cb.createQuery(Part.class);
        Root<Part> root = query.from(Part.class);

        List<Predicate> predicates = new ArrayList<>();

        // Base condition - active parts only
        predicates.add(cb.equal(root.get("active"), true));

        // Part type filter
        if (partType != null) {
            predicates.add(cb.equal(root.get("partType"), partType));
        }

        // Item type filter (GPU, CPU, RAM, etc.)
        if (itemType != null && !itemType.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("itemType"), itemType));
        }

        // Model name filter (RTX 5070, Ryzen 7 2700X, etc.)
        if (modelName != null && !modelName.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("modelName"), modelName));
        }

        // Price filters
        if (minPrice != null) {
            predicates.add(cb.and(
                    cb.isNotNull(root.get("price")),
                    cb.greaterThanOrEqualTo(root.get("price"), minPrice)));
        }

        if (maxPrice != null) {
            predicates.add(cb.and(
                    cb.isNotNull(root.get("price")),
                    cb.lessThanOrEqualTo(root.get("price"), maxPrice)));
        }

        // Marketplace filter
        if (marketplace != null && !marketplace.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("marketplace"), marketplace));
        }

        // Source filter
        if (source != null && !source.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("source"), source));
        }

        // Age filter
        if (maxAge != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("scrapedAt"), maxAge));
        }

        // Search filter
        if (search != null && !search.trim().isEmpty()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            Predicate titleSearch = cb.like(cb.lower(root.get("title")), searchPattern);
            Predicate descriptionSearch = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), searchPattern);
            Predicate brandSearch = cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), searchPattern);
            Predicate modelSearch = cb.like(cb.lower(cb.coalesce(root.get("model"), "")), searchPattern);

            predicates.add(cb.or(titleSearch, descriptionSearch, brandSearch, modelSearch));
        }

        // Apply all predicates
        query.where(predicates.toArray(new Predicate[0]));

        // Apply sorting
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            pageable.getSort().forEach(sortOrder -> {
                if (sortOrder.isAscending()) {
                    orders.add(cb.asc(root.get(sortOrder.getProperty())));
                } else {
                    orders.add(cb.desc(root.get(sortOrder.getProperty())));
                }
            });
            query.orderBy(orders);
        }

        // Execute query with pagination
        TypedQuery<Part> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Part> results = typedQuery.getResultList();

        // Count query for total elements
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Part> countRoot = countQuery.from(Part.class);
        countQuery.select(cb.count(countRoot));

        // Apply same predicates to count query
        List<Predicate> countPredicates = new ArrayList<>();
        countPredicates.add(cb.equal(countRoot.get("active"), true));

        if (partType != null) {
            countPredicates.add(cb.equal(countRoot.get("partType"), partType));
        }

        if (itemType != null && !itemType.trim().isEmpty()) {
            countPredicates.add(cb.equal(countRoot.get("itemType"), itemType));
        }

        if (modelName != null && !modelName.trim().isEmpty()) {
            countPredicates.add(cb.equal(countRoot.get("modelName"), modelName));
        }

        if (minPrice != null) {
            countPredicates.add(cb.and(
                    cb.isNotNull(countRoot.get("price")),
                    cb.greaterThanOrEqualTo(countRoot.get("price"), minPrice)));
        }

        if (maxPrice != null) {
            countPredicates.add(cb.and(
                    cb.isNotNull(countRoot.get("price")),
                    cb.lessThanOrEqualTo(countRoot.get("price"), maxPrice)));
        }

        if (marketplace != null && !marketplace.trim().isEmpty()) {
            countPredicates.add(cb.equal(countRoot.get("marketplace"), marketplace));
        }

        if (source != null && !source.trim().isEmpty()) {
            countPredicates.add(cb.equal(countRoot.get("source"), source));
        }

        if (maxAge != null) {
            countPredicates.add(cb.greaterThanOrEqualTo(countRoot.get("scrapedAt"), maxAge));
        }

        if (search != null && !search.trim().isEmpty()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            Predicate titleSearch = cb.like(cb.lower(countRoot.get("title")), searchPattern);
            Predicate descriptionSearch = cb.like(cb.lower(cb.coalesce(countRoot.get("description"), "")),
                    searchPattern);
            Predicate brandSearch = cb.like(cb.lower(cb.coalesce(countRoot.get("brand"), "")), searchPattern);
            Predicate modelSearch = cb.like(cb.lower(cb.coalesce(countRoot.get("model"), "")), searchPattern);

            countPredicates.add(cb.or(titleSearch, descriptionSearch, brandSearch, modelSearch));
        }

        countQuery.where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }
}