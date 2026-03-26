package com.dietbuilder.repository;

import com.dietbuilder.model.DietPlan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DietPlanRepository extends MongoRepository<DietPlan, String> {
    List<DietPlan> findByProfileIdOrderByCreatedAtDesc(String profileId);
}
