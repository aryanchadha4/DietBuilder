package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "saved_meals")
public class SavedMeal {
    @Id
    private String id;

    private String userId;
    private String profileId;

    private String sourcePlanId;
    private Integer sourceDayIndex;
    private Integer sourceMealIndex;

    private DietPlan.Meal meal;

    /** Optional tags for future filtering/search. */
    private List<String> tags = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;
}
