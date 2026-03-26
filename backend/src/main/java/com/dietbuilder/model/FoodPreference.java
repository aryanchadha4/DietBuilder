package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "food_preferences")
@CompoundIndex(name = "user_food_idx", def = "{'userId': 1, 'foodName': 1}", unique = true)
public class FoodPreference {

    @Id
    private String id;

    private String userId;

    private String foodName;

    private PreferenceType type;

    private String reason;

    private String sourcePlanId;

    @CreatedDate
    private Instant createdAt;

    private Instant expiresAt;

    public enum PreferenceType {
        PERMANENT,
        TEMPORARY
    }
}
