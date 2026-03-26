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
import java.util.List;
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
}
