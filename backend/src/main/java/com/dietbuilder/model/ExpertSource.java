package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "expert_sources")
public class ExpertSource {
    @Id
    private String id;

    private String title;
    private String authors;
    private String journal;
    private String organization;
    private String publicationDate;
    private String doi;
    private String url;

    private SourceType sourceType;

    private String abstractText;
    private String summary;
    private List<String> keyFindings = new ArrayList<>();
    private List<String> topics = new ArrayList<>();
    private List<String> relevantConditions = new ArrayList<>();
    private List<String> relevantGoals = new ArrayList<>();
    private List<String> ageGroups = new ArrayList<>();
    private List<String> genderRelevance = new ArrayList<>();

    private double credibilityScore;
    private List<Double> embedding = new ArrayList<>();

    private boolean active = true;
    private Instant lastVerifiedAt;

    @CreatedDate
    private Instant indexedAt;

    @LastModifiedDate
    private Instant updatedAt;

    public enum SourceType {
        PEER_REVIEWED,
        META_ANALYSIS,
        SYSTEMATIC_REVIEW,
        GOVERNMENT_GUIDELINE,
        CLINICAL_GUIDELINE,
        POSITION_STATEMENT,
        TEXTBOOK,
        EXPERT_CONSENSUS
    }
}
