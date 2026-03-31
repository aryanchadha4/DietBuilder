package com.dietbuilder.repository;

import com.dietbuilder.model.SavedMeal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SavedMealRepository extends MongoRepository<SavedMeal, String> {
    List<SavedMeal> findByUserIdOrderByCreatedAtDesc(String userId);
    List<SavedMeal> findByUserIdAndProfileIdOrderByCreatedAtDesc(String userId, String profileId);
}
