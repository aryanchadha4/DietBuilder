package com.dietbuilder.repository;

import com.dietbuilder.model.FairnessReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FairnessReportRepository extends MongoRepository<FairnessReport, String> {
    List<FairnessReport> findAllByOrderByGeneratedAtDesc();
}
