package com.dietbuilder.repository;

import com.dietbuilder.model.ConsentRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ConsentRecordRepository extends MongoRepository<ConsentRecord, String> {
    Optional<ConsentRecord> findByProfileId(String profileId);
}
