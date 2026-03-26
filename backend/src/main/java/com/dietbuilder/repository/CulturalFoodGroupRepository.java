package com.dietbuilder.repository;

import com.dietbuilder.model.CulturalFoodGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CulturalFoodGroupRepository extends MongoRepository<CulturalFoodGroup, String> {
    List<CulturalFoodGroup> findByCulture(String culture);
    List<CulturalFoodGroup> findByCultureAndCategory(String culture, String category);
}
