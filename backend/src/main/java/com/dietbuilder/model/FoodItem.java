package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Document(collection = "food_items")
public class FoodItem {
    @Id
    private String id;
    private int fdcId;
    private String description;
    private String foodCategory;
    private Map<String, NutrientValue> nutrients;
    private List<String> culturalTags;
    private List<String> allergenTags;

    @Data
    public static class NutrientValue {
        private double amount;
        private String unit;
        public NutrientValue() {}
        public NutrientValue(double amount, String unit) {
            this.amount = amount;
            this.unit = unit;
        }
    }
}
