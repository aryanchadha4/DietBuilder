package com.dietbuilder.repository;

import com.dietbuilder.model.RecommendationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationLogRepository extends MongoRepository<RecommendationLog, String> {
    Optional<RecommendationLog> findByPlanId(String planId);
    List<RecommendationLog> findByProfileId(String profileId);
}
