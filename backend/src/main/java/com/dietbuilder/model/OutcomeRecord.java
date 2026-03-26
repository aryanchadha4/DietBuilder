package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "outcome_records")
public class OutcomeRecord {
    @Id
    private String id;
    private String profileId;
    private String planId;
    private Instant recordedAt;
    private Double weightKg;
    private Double adherencePercent;
    private List<String> symptoms;
    private Map<String, Double> labResults;
    private String trainingResponse;
    private String notes;
}
