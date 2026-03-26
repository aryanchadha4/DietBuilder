package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "safety_alerts")
public class SafetyAlert {
    @Id
    private String id;
    private String profileId;
    private String planId;
    private String checkType;
    private String severity;
    private String message;
    private String recommendation;
    @CreatedDate
    private Instant createdAt;
}
