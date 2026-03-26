package com.dietbuilder.controller;

import com.dietbuilder.model.FoodItem;
import com.dietbuilder.service.CulturalSubstitutionService;
import com.dietbuilder.service.NutrientDatabaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class FoodController {
    private final NutrientDatabaseService nutrientDatabaseService;
    private final CulturalSubstitutionService culturalSubstitutionService;

    @GetMapping("/search")
    public List<FoodItem> searchFoods(@RequestParam String query, @RequestParam(required = false) String culture) {
        return nutrientDatabaseService.searchFoods(query, culture);
    }

    @GetMapping("/{fdcId}")
    public FoodItem getFood(@PathVariable int fdcId) {
        return nutrientDatabaseService.getFoodByFdcId(fdcId).orElseThrow(() -> new RuntimeException("Food not found: " + fdcId));
    }

    @GetMapping("/substitutions")
    public List<CulturalSubstitutionService.SubstitutionOption> getSubstitutions(@RequestParam int fdcId, @RequestParam String targetCulture) {
        return culturalSubstitutionService.getSubstitutions(fdcId, targetCulture);
    }

    @GetMapping("/cultures")
    public Set<String> listCultures() {
        return culturalSubstitutionService.listCultures();
    }
}
