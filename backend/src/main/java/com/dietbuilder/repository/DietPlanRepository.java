package com.dietbuilder.repository;

import com.dietbuilder.model.DietPlan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DietPlanRepository extends MongoRepository<DietPlan, String> {
    List<DietPlan> findByProfileIdOrderByCreatedAtDesc(String profileId);

    List<DietPlan> findByProfileIdAndUserRatingIsNotNullOrderByRatedAtDesc(String profileId, Pageable pageable);
}
