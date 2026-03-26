package com.dietbuilder.repository;

import com.dietbuilder.model.FoodItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends MongoRepository<FoodItem, String> {
    Optional<FoodItem> findByFdcId(int fdcId);
    List<FoodItem> findByDescriptionContainingIgnoreCase(String query);
    List<FoodItem> findByCulturalTagsContaining(String culturalTag);
    @Query("{ 'description': { $regex: ?0, $options: 'i' }, 'culturalTags': ?1 }")
    List<FoodItem> searchByQueryAndCulture(String query, String culture);
    @Query("{ 'description': { $regex: ?0, $options: 'i' } }")
    List<FoodItem> searchByQuery(String query);
}
