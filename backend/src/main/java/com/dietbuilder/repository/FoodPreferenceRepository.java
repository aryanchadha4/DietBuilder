package com.dietbuilder.repository;

import com.dietbuilder.model.FoodPreference;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FoodPreferenceRepository extends MongoRepository<FoodPreference, String> {

    List<FoodPreference> findByUserId(String userId);

    List<FoodPreference> findByUserIdAndType(String userId, FoodPreference.PreferenceType type);

    Optional<FoodPreference> findByUserIdAndFoodNameIgnoreCase(String userId, String foodName);

    void deleteByUserIdAndFoodNameIgnoreCase(String userId, String foodName);

    void deleteByUserIdAndType(String userId, FoodPreference.PreferenceType type);

    List<FoodPreference> findByTypeAndExpiresAtBefore(FoodPreference.PreferenceType type, Instant cutoff);
}
