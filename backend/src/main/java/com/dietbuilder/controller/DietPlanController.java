package com.dietbuilder.controller;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.service.DietRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DietPlanController {

    private final DietRecommendationService recommendationService;

    @PostMapping("/recommend/{profileId}")
    public ResponseEntity<DietPlan> generateRecommendation(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) List<String> cuisines,
            @RequestParam(required = false) String planMode,
            @RequestParam(required = false) String hybridDepth,
            @RequestParam(required = false) Integer syncDays) {
        List<String> c = cuisines != null ? cuisines : List.of();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recommendationService.generateRecommendation(profileId, days, List.of(), c,
                        planMode, hybridDepth, syncDays));
    }

    /** Append remaining days for a partial hybrid plan ({@code days.size() < totalDays}). */
    @PostMapping("/diet-plans/{planId}/complete-days")
    public ResponseEntity<DietPlan> completePlanDays(@PathVariable String planId) {
        return ResponseEntity.ok(recommendationService.completePartialPlanDays(planId));
    }

    @PostMapping("/recommend/{profileId}/regenerate")
    public ResponseEntity<DietPlan> regeneratePlan(
            @PathVariable String profileId,
            @RequestBody RegenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recommendationService.regeneratePlan(profileId, request));
    }

    @GetMapping("/diet-plans/{profileId}")
    public List<DietPlan> getDietPlans(@PathVariable String profileId) {
        return recommendationService.getDietPlans(profileId);
    }

    @GetMapping("/diet-plans")
    public List<DietPlan> getAllDietPlans() {
        return recommendationService.getAllDietPlans();
    }

    @DeleteMapping("/diet-plans/{planId}/meals")
    public DietPlan removeMeal(
            @PathVariable String planId,
            @RequestParam int mealIndex,
            @RequestParam(required = false) Integer dayIndex) {
        return recommendationService.removeMealFromPlan(planId, dayIndex, mealIndex);
    }

    @PostMapping("/diet-plans/{planId}/regenerate-removed")
    public ResponseEntity<DietPlan> regenerateRemovedMeals(
            @PathVariable String planId,
            @RequestBody(required = false) RegenerateRemovedRequest request) {
        return ResponseEntity.ok(recommendationService.regenerateRemovedMeals(planId, request));
    }

    @lombok.Data
    public static class RegenerateRequest {
        private String parentPlanId;
        private List<String> rejectedFoods;
        private int days;
        /** If omitted, parent plan's cuisine preferences are used. */
        private List<String> cuisines;
    }

    @lombok.Data
    public static class RegenerateRemovedRequest {
        private List<String> rejectedFoods;
    }
}
