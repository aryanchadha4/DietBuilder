package com.dietbuilder.controller;

import com.dietbuilder.model.FoodPreference;
import com.dietbuilder.service.FoodPreferenceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/food-preferences")
@RequiredArgsConstructor
public class FoodPreferenceController {

    private final FoodPreferenceService foodPreferenceService;

    @GetMapping
    public List<FoodPreference> listActive() {
        return foodPreferenceService.getActivePreferences();
    }

    @PostMapping
    public ResponseEntity<FoodPreference> addDislike(@RequestBody AddDislikeRequest request) {
        FoodPreference pref = foodPreferenceService.addDislike(
                request.getFoodName(),
                request.getType(),
                request.getReason(),
                request.getSourcePlanId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pref);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeDislike(@PathVariable String id) {
        foodPreferenceService.removeDislike(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public FoodPreference updatePreference(@PathVariable String id,
                                           @RequestBody UpdatePreferenceRequest request) {
        return foodPreferenceService.updatePreference(id, request.getType());
    }

    @DeleteMapping("/reset-temporary")
    public ResponseEntity<Void> resetTemporary() {
        foodPreferenceService.resetTemporaryDislikes();
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class AddDislikeRequest {
        private String foodName;
        private FoodPreference.PreferenceType type;
        private String reason;
        private String sourcePlanId;
    }

    @Data
    public static class UpdatePreferenceRequest {
        private FoodPreference.PreferenceType type;
    }
}
