package com.dietbuilder.service;

import com.dietbuilder.model.CulturalFoodGroup;
import com.dietbuilder.model.FoodItem;
import com.dietbuilder.repository.CulturalFoodGroupRepository;
import com.dietbuilder.repository.FoodItemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CulturalSubstitutionService {

    private final CulturalFoodGroupRepository culturalFoodGroupRepository;
    private final FoodItemRepository foodItemRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, List<SubstitutionOption>> substitutionCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (culturalFoodGroupRepository.count() > 0) {
            log.info("Cultural food groups already loaded");
            return;
        }
        try {
            InputStream is = new ClassPathResource("data/cultural-food-groups.json").getInputStream();
            List<CulturalFoodGroup> groups = objectMapper.readValue(is, new TypeReference<List<CulturalFoodGroup>>() {});
            culturalFoodGroupRepository.saveAll(groups);
            log.info("Seeded {} cultural food groups", groups.size());
        } catch (Exception e) {
            log.error("Failed to seed cultural food groups", e);
        }
    }

    public List<SubstitutionOption> getSubstitutions(int fdcId, String targetCulture) {
        String cacheKey = fdcId + "|" + (targetCulture != null ? targetCulture.toLowerCase(Locale.ROOT) : "");
        List<SubstitutionOption> cached = substitutionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<FoodItem> sourceOpt = foodItemRepository.findByFdcId(fdcId);
        if (sourceOpt.isEmpty()) {
            substitutionCache.put(cacheKey, List.of());
            return List.of();
        }
        FoodItem source = sourceOpt.get();
        String sourceCategory = guessCategory(source);
        List<CulturalFoodGroup> targetGroups = culturalFoodGroupRepository.findByCultureAndCategory(targetCulture, sourceCategory);
        if (targetGroups.isEmpty()) targetGroups = culturalFoodGroupRepository.findByCulture(targetCulture);
        List<SubstitutionOption> out = targetGroups.stream().flatMap(g -> g.getFoods().stream()).map(eq -> {
            SubstitutionOption opt = new SubstitutionOption();
            opt.setFdcId(eq.getFdcId());
            opt.setName(eq.getName());
            opt.setServingGrams(eq.getTypicalServingGrams());
            opt.setCulture(targetCulture);
            opt.setCategory(sourceCategory);
            return opt;
        }).collect(Collectors.toList());
        substitutionCache.put(cacheKey, out);
        return out;
    }

    public Set<String> listCultures() {
        return culturalFoodGroupRepository.findAll().stream()
                .map(CulturalFoodGroup::getCulture)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private String guessCategory(FoodItem food) {
        String cat = food.getFoodCategory() != null ? food.getFoodCategory().toLowerCase() : "";
        String desc = food.getDescription() != null ? food.getDescription().toLowerCase() : "";
        if (cat.contains("grain") || desc.contains("rice") || desc.contains("bread")) return "grain_staple";
        if (cat.contains("meat") || cat.contains("poultry") || cat.contains("fish")) return "protein_source";
        if (cat.contains("vegetable")) return "vegetable";
        if (cat.contains("fruit")) return "fruit";
        if (cat.contains("dairy")) return "dairy_alternative";
        if (cat.contains("legume") || desc.contains("bean") || desc.contains("lentil")) return "legume";
        if (cat.contains("oil") || cat.contains("fat")) return "cooking_fat";
        return "other";
    }

    @Data
    public static class SubstitutionOption {
        private int fdcId;
        private String name;
        private double servingGrams;
        private String culture;
        private String category;
    }
}
