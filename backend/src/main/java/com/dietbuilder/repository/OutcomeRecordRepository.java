package com.dietbuilder.repository;

import com.dietbuilder.model.OutcomeRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OutcomeRecordRepository extends MongoRepository<OutcomeRecord, String> {
    List<OutcomeRecord> findByProfileIdOrderByRecordedAtDesc(String profileId);
    List<OutcomeRecord> findByPlanId(String planId);
}
