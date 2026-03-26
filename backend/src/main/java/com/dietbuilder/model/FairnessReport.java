package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "fairness_reports")
public class FairnessReport {
    @Id
    private String id;
    private Instant generatedAt;
    private Map<String, GroupMetrics> byGender;
    private Map<String, GroupMetrics> byAgeGroup;
    private Map<String, GroupMetrics> byCulture;
    private List<String> flaggedDisparities;
    private int totalPlansAnalyzed;

    @Data
    public static class GroupMetrics {
        private int count;
        private double avgAdequacyScore;
        private double avgCalorieAccuracy;
        private double safetyAlertRate;
    }
}
