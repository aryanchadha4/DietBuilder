package com.dietbuilder.service;

import com.dietbuilder.model.CulturalFoodGroup;
import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.OutcomeRecord;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.CulturalFoodGroupRepository;
import com.dietbuilder.repository.DietPlanRepository;
import com.dietbuilder.repository.OutcomeRecordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LongitudinalAdaptationService {

    private final OutcomeRecordRepository outcomeRecordRepository;
    private final DietPlanRepository dietPlanRepository;
    private final CulturalFoodGroupRepository culturalFoodGroupRepository;
    private final OpenAIService openAIService;
    private final SafetyGuardrailService safetyGuardrailService;
    private final OutcomeTrackingService outcomeTrackingService;
    private final DietRecommendationService dietRecommendationService;
    private final FoodPreferenceService foodPreferenceService;

    public AdaptationAssessment shouldAdaptPlan(String profileId) {
        AdaptationAssessment assessment = new AdaptationAssessment();
        assessment.setReasons(new ArrayList<>());
        assessment.setAdaptRecommended(false);

        List<OutcomeRecord> records = outcomeRecordRepository.findByProfileIdOrderByRecordedAtDesc(profileId);
        if (records.size() < 3) {
            return assessment;
        }

        OutcomeTrackingService.OutcomeTrends trends = outcomeTrackingService.computeTrends(profileId);

        if (trends.getAvgAdherence() < 60) {
            assessment.getReasons().add("Low adherence (" + String.format("%.0f", trends.getAvgAdherence()) + "%) suggests plan is too restrictive");
            assessment.setAdaptRecommended(true);
        }

        if (Math.abs(trends.getWeightChangeKg()) < 0.2 && records.size() >= 5) {
            assessment.getReasons().add("Weight plateau detected over " + records.size() + " records");
            assessment.setAdaptRecommended(true);
        }

        List<OutcomeRecord> recent = records.subList(0, Math.min(3, records.size()));
        long symptomatic = recent.stream()
                .filter(r -> r.getSymptoms() != null && !r.getSymptoms().isEmpty())
                .count();
        if (symptomatic >= 2) {
            assessment.getReasons().add("New symptoms reported in " + symptomatic + " of last 3 records");
            assessment.setAdaptRecommended(true);
        }

        long regressingTraining = recent.stream()
                .filter(r -> "regressing".equalsIgnoreCase(r.getTrainingResponse()))
                .count();
        if (regressingTraining >= 2) {
            assessment.getReasons().add("Training performance regressing");
            assessment.setAdaptRecommended(true);
        }

        return assessment;
    }

    public DietPlan generateAdaptedPlan(String profileId) {
        UserProfile profile = dietRecommendationService.getProfile(profileId);

        List<DietPlan> previousPlans = dietPlanRepository.findByProfileIdOrderByCreatedAtDesc(profileId);
        DietPlan previousPlan = previousPlans.isEmpty() ? null : previousPlans.get(0);

        OutcomeTrackingService.OutcomeTrends trends = outcomeTrackingService.computeTrends(profileId);
        AdaptationAssessment assessment = shouldAdaptPlan(profileId);

        SafetyGuardrailService.SafetyCheckResult preCheck = safetyGuardrailService.runPreChecks(profile);
        if (preCheck.isBlocked()) {
            DietPlan blocked = new DietPlan();
            blocked.setProfileId(profileId);
            blocked.setSafetyAlerts(preCheck.getAlerts());
            blocked.setSafetyCleared(false);
            blocked.setNotes("Adapted plan generation blocked by safety checks.");
            return dietPlanRepository.save(blocked);
        }

        List<String> cuisineList = new ArrayList<>();
        if (previousPlan != null && previousPlan.getCuisinePreferences() != null) {
            for (String c : previousPlan.getCuisinePreferences()) {
                if (c != null && !c.isBlank() && !cuisineList.contains(c.trim())) {
                    cuisineList.add(c.trim());
                }
            }
        }

        List<CulturalFoodGroup> culturalFoods = List.of();
        if (!cuisineList.isEmpty()) {
            List<CulturalFoodGroup> merged = new ArrayList<>();
            Set<String> seenIds = new LinkedHashSet<>();
            for (String culture : cuisineList) {
                for (CulturalFoodGroup g : culturalFoodGroupRepository.findByCulture(culture)) {
                    if (g.getId() != null) {
                        if (seenIds.add(g.getId())) merged.add(g);
                    } else {
                        merged.add(g);
                    }
                }
            }
            culturalFoods = merged;
        }

        int numDays = (previousPlan != null && previousPlan.getTotalDays() > 0) ? previousPlan.getTotalDays() : 14;
        List<String> dislikedFoods = foodPreferenceService.getActiveDislikedFoodNamesForCurrentUser();
        DietPlan adaptedPlan = openAIService.generateAdaptedPlan(profile, previousPlan, trends, assessment,
                cuisineList, culturalFoods, numDays, dislikedFoods);
        adaptedPlan.setProfileId(profileId);
        adaptedPlan.setCuisinePreferences(new ArrayList<>(cuisineList));
        if (previousPlan != null) {
            adaptedPlan.setParentPlanId(previousPlan.getId());
            adaptedPlan.setVersion(previousPlan.getVersion() + 1);
        }

        SafetyGuardrailService.SafetyCheckResult postCheck = safetyGuardrailService.runPostChecks(adaptedPlan, profile);
        List<DietPlan.SafetyAlert> allAlerts = new ArrayList<>(preCheck.getAlerts());
        allAlerts.addAll(postCheck.getAlerts());
        adaptedPlan.setSafetyAlerts(allAlerts);
        adaptedPlan.setSafetyCleared(!postCheck.isBlocked());

        return dietPlanRepository.save(adaptedPlan);
    }

    @Data
    public static class AdaptationAssessment {
        private boolean adaptRecommended;
        private List<String> reasons;
    }
}
