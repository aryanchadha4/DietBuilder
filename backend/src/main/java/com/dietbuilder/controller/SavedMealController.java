package com.dietbuilder.controller;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.SavedMeal;
import com.dietbuilder.service.SavedMealService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-meals")
@RequiredArgsConstructor
public class SavedMealController {
    private final SavedMealService savedMealService;

    @PostMapping
    public ResponseEntity<SavedMeal> saveMeal(@RequestBody SaveMealRequest request) {
        SavedMeal saved = savedMealService.saveFromPlanMeal(
                request.getPlanId(),
                request.getDayIndex(),
                request.getMealIndex());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<SavedMeal> list(@RequestParam(required = false) String profileId) {
        return savedMealService.listSavedMeals(profileId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        savedMealService.deleteSavedMeal(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/insert")
    public DietPlan insert(@PathVariable String id, @RequestBody InsertSavedMealRequest request) {
        return savedMealService.insertSavedMeal(
                id,
                request.getPlanId(),
                request.getDayIndex(),
                request.getMealIndex());
    }

    @Data
    public static class SaveMealRequest {
        private String planId;
        private Integer dayIndex;
        private int mealIndex;
    }

    @Data
    public static class InsertSavedMealRequest {
        private String planId;
        private Integer dayIndex;
        private int mealIndex;
    }
}
