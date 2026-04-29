package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "diet_plans")
public class DietPlan {

    @Id
    private String id;

    private String profileId;

    /** Culture slugs (e.g. south-asian); used with cultural_food_groups the same way as the former profile field. */
    private List<String> cuisinePreferences = new ArrayList<>();

    private String planContent;

    // Legacy single-day fields (kept for backward compat)
    private List<Meal> meals = new ArrayList<>();
    private int dailyCalories;
    private MacroBreakdown macroBreakdown;

    // Multi-day plan structure
    private List<DayPlan> days = new ArrayList<>();
    private int totalDays;
    private List<RemovedMealSlot> removedMealSlots = new ArrayList<>();

    private String notes;

    private String goalType;
    private int goalTimelineWeeks;

    private NutrientAudit nutrientAudit;

    private List<EvidenceTag> evidenceTags = new ArrayList<>();
    private List<String> sourceIds = new ArrayList<>();
    private String confidenceStatement;
    private double evidenceConfidenceScore;
    private List<ConflictNote> conflictNotes = new ArrayList<>();

    private List<NutrientAdequacy> nutrientAdequacy = new ArrayList<>();

    private PlanConfidence confidence;

    private SafetyScreening safetyScreening;

    private List<FoodExplanation> foodExplanations = new ArrayList<>();

    private List<SafetyAlert> safetyAlerts = new ArrayList<>();
    private boolean safetyCleared;

    private String parentPlanId;
    private int version = 1;

    /** User rating 1-5 for this plan; null if not rated. */
    private Integer userRating;

    /** Optional short free-text feedback with the rating. */
    private String ratingFeedback;

    private Instant ratedAt;

    @CreatedDate
    private Instant createdAt;

    @Data
    public static class Meal {
        private String name;
        private List<MealFood> foods = new ArrayList<>();
        private int calories;
        private double proteinGrams;
        private double carbsGrams;
        private double fatGrams;
        private double fiberGrams;
        private Map<String, Double> micronutrients;
        private String rationale;
    }

    @Data
    public static class MealFood {
        private String fdcId;
        private String name;
        private double quantityGrams;
        private Map<String, Double> keyNutrients;
    }

    @Data
    public static class MacroBreakdown {
        private double proteinPercent;
        private double carbsPercent;
        private double fatPercent;
    }

    @Data
    public static class NutrientAudit {
        private Map<String, NutrientStatus> nutrients;
        private double adequacyScore;
        private int knownNutrientCount;
        private int unknownNutrientCount;
        private double dataCoveragePercent;
        private boolean proteinGoalAdjusted;
    }

    @Data
    public static class NutrientStatus {
        private double planned;
        private double rda;
        private double ul;
        private String unit;
        private String status;
        /** "RDA" (default) or "GOAL_RANGE" for goal-aware targets like protein. */
        private String targetType;
        /** True when this nutrient was computed with available data. */
        private boolean known = true;
    }

    @Data
    public static class NutrientAdequacy {
        private String nutrient;
        private double amount;
        private String unit;
        private double driTarget;
        private double upperLimit;
        private double percentDri;
        private String status;
    }

    @Data
    public static class EvidenceTag {
        private String claim;
        private EvidenceLevel level;
        private String source;
        private String explanation;
        private String sourceId;
        private String doi;
        private String url;
        private String citationText;
        private double relevanceScore;
        private String simpleSummary;
    }

    public enum EvidenceLevel {
        GUIDELINE_BACKED,
        META_ANALYSIS,
        OBSERVATIONAL,
        LOW_CONFIDENCE
    }

    @Data
    public static class PlanConfidence {
        private double overallScore;
        private List<String> assumptions = new ArrayList<>();
        private List<String> limitations = new ArrayList<>();
        private String evidenceBasis;
    }

    @Data
    public static class SafetyScreening {
        private boolean passed;
        private List<Warning> warnings = new ArrayList<>();
        private List<String> blockers = new ArrayList<>();
    }

    @Data
    public static class Warning {
        private String severity;
        private String message;
        private String source;
    }

    @Data
    public static class FoodExplanation {
        private String food;
        private String reason;
        private List<String> nutrientGapsFilled = new ArrayList<>();
        private String evidenceBasis;
    }

    @Data
    public static class SafetyAlert {
        private String checkType;
        private String severity;
        private String message;
        private String recommendation;
    }

    @Data
    public static class DayPlan {
        private int dayNumber;
        private String label;
        private List<Meal> meals = new ArrayList<>();
        private int dailyCalories;
        private MacroBreakdown macroBreakdown;
    }

    @Data
    public static class RemovedMealSlot {
        private String slotId;
        private Integer dayIndex;
        private int originalMealIndex;
        private String mealName;
        private Instant removedAt;
        private Meal originalMealSnapshot;
    }

    @Data
    public static class ConflictNote {
        private String topic;
        private String summary;
        private List<String> supportingSourceIds = new ArrayList<>();
        private List<String> opposingSourceIds = new ArrayList<>();
        private String resolution;
        private String resolutionBasis;
    }
}
