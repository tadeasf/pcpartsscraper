package com.tadeasfort.pcpartsscraper.repository;

import com.tadeasfort.pcpartsscraper.model.PartType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartTypeRepository extends JpaRepository<PartType, Long> {

    Optional<PartType> findByItemTypeAndModelName(String itemType, String modelName);

    List<PartType> findByItemType(String itemType);

    List<PartType> findByCategory(String category);

    List<PartType> findByBrand(String brand);

    @Query("SELECT pt FROM PartType pt WHERE pt.itemType = :itemType ORDER BY pt.averagePrice ASC")
    List<PartType> findByItemTypeOrderByAveragePrice(@Param("itemType") String itemType);

    @Query("SELECT pt FROM PartType pt WHERE pt.itemType = :itemType ORDER BY pt.totalListings DESC")
    List<PartType> findByItemTypeOrderByPopularity(@Param("itemType") String itemType);

    @Query("SELECT pt FROM PartType pt WHERE pt.releaseYear = :year ORDER BY pt.modelName")
    List<PartType> findByReleaseYear(@Param("year") Integer year);

    @Query("SELECT DISTINCT pt.itemType FROM PartType pt ORDER BY pt.itemType")
    List<String> findDistinctItemTypes();

    @Query("SELECT DISTINCT pt.brand FROM PartType pt WHERE pt.itemType = :itemType ORDER BY pt.brand")
    List<String> findDistinctBrandsByItemType(@Param("itemType") String itemType);

    @Query("SELECT DISTINCT pt.series FROM PartType pt WHERE pt.itemType = :itemType AND pt.brand = :brand ORDER BY pt.series")
    List<String> findDistinctSeriesByItemTypeAndBrand(@Param("itemType") String itemType, @Param("brand") String brand);

    @Query("SELECT pt FROM PartType pt WHERE pt.activeListings > 0 ORDER BY pt.averagePrice ASC")
    List<PartType> findActiveComponentsOrderByPrice();

    @Query("SELECT pt FROM PartType pt WHERE pt.activeListings > 0 ORDER BY pt.totalListings DESC")
    List<PartType> findActiveComponentsOrderByPopularity();

    @Query("SELECT pt FROM PartType pt WHERE pt.modelName = :modelName AND pt.itemType = :itemType")
    Optional<PartType> findByModelNameAndItemType(@Param("modelName") String modelName,
            @Param("itemType") String itemType);
}