package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "cultural_food_groups")
public class CulturalFoodGroup {
    @Id
    private String id;
    private String culture;
    private String category;
    private List<FoodEquivalent> foods;

    @Data
    public static class FoodEquivalent {
        private int fdcId;
        private String name;
        private double typicalServingGrams;
    }
}
