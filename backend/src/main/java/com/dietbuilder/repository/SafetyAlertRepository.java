package com.dietbuilder.repository;

import com.dietbuilder.model.SafetyAlert;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SafetyAlertRepository extends MongoRepository<SafetyAlert, String> {
    List<SafetyAlert> findByProfileId(String profileId);
    List<SafetyAlert> findByPlanId(String planId);
}
