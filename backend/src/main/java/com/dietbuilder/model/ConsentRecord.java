package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "consent_records")
public class ConsentRecord {
    public static final String CONSENT_DATA_STORAGE = "data_storage";
    public static final String CONSENT_AI_PROCESSING = "ai_processing";
    public static final String CONSENT_OUTCOME_ANALYTICS = "outcome_analytics";
    public static final String CONSENT_FAIRNESS_RESEARCH = "fairness_research";

    @Id
    private String id;
    private String profileId;
    private Map<String, Boolean> consents;
    private Instant grantedAt;
    private Instant revokedAt;
    private String ipHash;
}
