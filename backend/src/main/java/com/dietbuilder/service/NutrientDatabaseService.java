package com.dietbuilder.service;

import com.dietbuilder.model.FoodItem;
import com.dietbuilder.repository.FoodItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutrientDatabaseService {

    private final FoodItemRepository foodItemRepository;
    private final ObjectMapper objectMapper;
    @Value("${usda.api.key:}")
    private String usdaApiKey;
    private final WebClient.Builder webClientBuilder;

    public List<FoodItem> searchFoods(String query, String culturalFilter) {
        List<FoodItem> results;
        if (culturalFilter != null && !culturalFilter.isBlank())
            results = foodItemRepository.searchByQueryAndCulture(query, culturalFilter);
        else
            results = foodItemRepository.searchByQuery(query);
        if (results.isEmpty() && usdaApiKey != null && !usdaApiKey.isBlank())
            results = lookupFromUSDA(query);
        return results;
    }

    public Optional<FoodItem> getFoodByFdcId(int fdcId) {
        return foodItemRepository.findByFdcId(fdcId);
    }

    /**
     * Resolves food by FDC ID using local cache first, then USDA API if configured.
     */
    public Optional<FoodItem> getOrFetchFoodByFdcId(int fdcId) {
        Optional<FoodItem> cached = foodItemRepository.findByFdcId(fdcId);
        if (cached.isPresent()) {
            return cached;
        }
        if (usdaApiKey == null || usdaApiKey.isBlank() || fdcId <= 0) {
            return Optional.empty();
        }
        return fetchFoodByFdcIdFromUSDA(fdcId);
    }

    private List<FoodItem> lookupFromUSDA(String query) {
        try {
            WebClient client = webClientBuilder.baseUrl("https://api.nal.usda.gov/fdc/v1").build();
            String response = client.get()
                    .uri(u -> u.path("/foods/search")
                            .queryParam("api_key", usdaApiKey)
                            .queryParam("query", query)
                            .queryParam("pageSize", 5).build())
                    .retrieve().bodyToMono(String.class).block();
            JsonNode root = objectMapper.readTree(response);
            List<FoodItem> items = new ArrayList<>();
            for (JsonNode fn : root.path("foods")) {
                FoodItem item = new FoodItem();
                item.setFdcId(fn.path("fdcId").asInt());
                item.setDescription(fn.path("description").asText());
                items.add(item);
                foodItemRepository.save(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("USDA lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Optional<FoodItem> fetchFoodByFdcIdFromUSDA(int fdcId) {
        try {
            WebClient client = webClientBuilder.baseUrl("https://api.nal.usda.gov/fdc/v1").build();
            String response = client.get()
                    .uri(u -> u.path("/food/{fdcId}")
                            .queryParam("api_key", usdaApiKey)
                            .build(fdcId))
                    .retrieve().bodyToMono(String.class).block();
            JsonNode root = objectMapper.readTree(response);
            FoodItem item = new FoodItem();
            item.setFdcId(root.path("fdcId").asInt());
            item.setDescription(root.path("description").asText(""));
            item.setFoodCategory(root.path("foodCategory").path("description").asText(""));
            item.setNutrients(parseNutrients(root.path("foodNutrients")));
            FoodItem saved = foodItemRepository.save(item);
            return Optional.of(saved);
        } catch (Exception e) {
            log.warn("USDA lookup by fdcId {} failed: {}", fdcId, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, FoodItem.NutrientValue> parseNutrients(JsonNode foodNutrientsNode) {
        Map<String, FoodItem.NutrientValue> nutrients = new LinkedHashMap<>();
        if (!foodNutrientsNode.isArray()) {
            return nutrients;
        }
        for (JsonNode n : foodNutrientsNode) {
            JsonNode nutrient = n.path("nutrient");
            String key = normalizeNutrientKey(
                    nutrient.path("name").asText(""),
                    nutrient.path("number").asText("")
            );
            if (key == null) {
                continue;
            }
            double amount = n.path("amount").asDouble(Double.NaN);
            if (Double.isNaN(amount)) {
                continue;
            }
            String unit = nutrient.path("unitName").asText("");
            nutrients.put(key, new FoodItem.NutrientValue(amount, unit));
        }
        return nutrients;
    }

    private String normalizeNutrientKey(String nutrientName, String nutrientNumber) {
        String n = nutrientName == null ? "" : nutrientName.toLowerCase(Locale.ROOT).trim();
        String num = nutrientNumber == null ? "" : nutrientNumber.trim();
        return switch (n) {
            case "energy", "energy (atwater general factors)" -> "energy";
            case "protein" -> "protein";
            case "total lipid (fat)" -> "fat";
            case "carbohydrate, by difference" -> "carbohydrate";
            case "fiber, total dietary" -> "fiber";
            case "calcium, ca" -> "calcium";
            case "iron, fe" -> "iron";
            case "magnesium, mg" -> "magnesium";
            case "zinc, zn" -> "zinc";
            case "vitamin a, rae" -> "vitamin_a";
            case "vitamin c, total ascorbic acid" -> "vitamin_c";
            case "vitamin d (d2 + d3)" -> "vitamin_d";
            case "vitamin e (alpha-tocopherol)" -> "vitamin_e";
            case "phylloquinone" -> "vitamin_k";
            case "vitamin b-6" -> "vitamin_b6";
            case "vitamin b-12" -> "vitamin_b12";
            case "folate, dfe" -> "folate";
            case "potassium, k" -> "potassium";
            case "sodium, na" -> "sodium";
            case "niacin" -> "niacin";
            case "phosphorus, p" -> "phosphorus";
            case "selenium, se" -> "selenium";
            case "copper, cu" -> "copper";
            case "manganese, mn" -> "manganese";
            case "chromium, cr" -> "chromium";
            default -> mapByNutrientNumber(num);
        };
    }

    private String mapByNutrientNumber(String nutrientNumber) {
        return switch (nutrientNumber) {
            case "1008" -> "energy";
            case "1003" -> "protein";
            case "1004" -> "fat";
            case "1005" -> "carbohydrate";
            case "1079" -> "fiber";
            case "1087" -> "calcium";
            case "1089" -> "iron";
            case "1090" -> "magnesium";
            case "1095" -> "zinc";
            case "1106" -> "vitamin_a";
            case "1162" -> "vitamin_c";
            case "1114" -> "vitamin_d";
            case "1109" -> "vitamin_e";
            case "1185" -> "vitamin_k";
            case "1175" -> "vitamin_b6";
            case "1178" -> "vitamin_b12";
            case "1177" -> "folate";
            case "1092" -> "potassium";
            case "1093" -> "sodium";
            default -> null;
        };
    }
}
