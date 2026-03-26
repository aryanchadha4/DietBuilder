package com.dietbuilder.repository;

import com.dietbuilder.model.ExpertSource;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ExpertSourceRepository extends MongoRepository<ExpertSource, String> {
    List<ExpertSource> findByActiveTrue();
    List<ExpertSource> findByActiveTrueAndSourceType(ExpertSource.SourceType sourceType);
}
