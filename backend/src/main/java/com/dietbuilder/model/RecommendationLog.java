package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "recommendation_logs")
public class RecommendationLog {
    @Id
    private String id;
    private String profileId;
    private String planId;
    private Instant timestamp;
    private String modelName;
    private String modelVersion;
    private String systemPrompt;
    private String userMessage;
    private String rawResponse;
    private Map<String, Object> profileSnapshot;
    private List<String> safetyChecksRun;
    private List<String> postProcessingSteps;
    private String nutrientDatabaseVersion;
    private long latencyMs;
    private String dataRetentionPolicy = "90_DAYS";
    private List<String> retrievedSourceIds;
    private int totalSourcesRetrieved;
    private int totalSourcesCited;
    private double avgSourceRelevance;
    private String retrievalQuery;
}
