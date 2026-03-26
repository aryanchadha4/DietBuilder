package com.dietbuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class NutrientReferenceService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode referenceData;

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("data/nutrient-references.json").getInputStream();
            referenceData = objectMapper.readTree(is);
            log.info("Loaded nutrient reference data with {} nutrients", referenceData.path("nutrients").size());
        } catch (Exception e) {
            log.error("Failed to load nutrient-references.json", e);
            throw new RuntimeException("Cannot start without nutrient reference data", e);
        }
    }

    public double getDRI(String nutrient, int age, String sex) {
        JsonNode nutrientNode = referenceData.path("nutrients").path(nutrient);
        if (nutrientNode.isMissingNode()) return 0;
        for (JsonNode group : nutrientNode.path("groups")) {
            if (matchesDemographic(group, age, sex)) return group.path("rda").asDouble(0);
        }
        return 0;
    }

    public double getUL(String nutrient, int age, String sex) {
        JsonNode nutrientNode = referenceData.path("nutrients").path(nutrient);
        if (nutrientNode.isMissingNode()) return Double.MAX_VALUE;
        for (JsonNode group : nutrientNode.path("groups")) {
            if (matchesDemographic(group, age, sex)) {
                double ul = group.path("ul").asDouble(-1);
                return ul < 0 ? Double.MAX_VALUE : ul;
            }
        }
        return Double.MAX_VALUE;
    }

    public String getUnit(String nutrient) {
        return referenceData.path("nutrients").path(nutrient).path("unit").asText("mg");
    }

    public Set<String> getAllTrackedNutrients() {
        Set<String> nutrients = new LinkedHashSet<>();
        referenceData.path("nutrients").fieldNames().forEachRemaining(nutrients::add);
        return nutrients;
    }

    public Map<String, NutrientAdequacy> validateAdequacy(Map<String, Double> nutrientTotals, int age, String sex) {
        Map<String, NutrientAdequacy> results = new LinkedHashMap<>();
        for (String nutrient : getAllTrackedNutrients()) {
            double planned = nutrientTotals.getOrDefault(nutrient, 0.0);
            double rda = getDRI(nutrient, age, sex);
            double ul = getUL(nutrient, age, sex);
            String unit = getUnit(nutrient);
            String status;
            if (rda > 0 && planned < rda * 0.5) status = "DEFICIENT";
            else if (rda > 0 && planned < rda * 0.8) status = "LOW";
            else if (ul < Double.MAX_VALUE && planned > ul) status = "EXCESSIVE";
            else status = "ADEQUATE";
            NutrientAdequacy adequacy = new NutrientAdequacy();
            adequacy.setPlanned(planned);
            adequacy.setRda(rda);
            adequacy.setUl(ul);
            adequacy.setUnit(unit);
            adequacy.setStatus(status);
            results.put(nutrient, adequacy);
        }
        return results;
    }

    public double computeAdequacyScore(Map<String, NutrientAdequacy> adequacyMap) {
        if (adequacyMap.isEmpty()) return 0;
        long adequate = adequacyMap.values().stream().filter(a -> "ADEQUATE".equals(a.getStatus())).count();
        return (double) adequate / adequacyMap.size() * 100.0;
    }

    private boolean matchesDemographic(JsonNode group, int age, String sex) {
        String groupSex = group.path("sex").asText("all");
        if (!"all".equals(groupSex) && !groupSex.equalsIgnoreCase(sex)) return false;
        int minAge = group.path("ageMin").asInt(0);
        int maxAge = group.path("ageMax").asInt(999);
        return age >= minAge && age <= maxAge;
    }

    @Data
    public static class NutrientAdequacy {
        private double planned;
        private double rda;
        private double ul;
        private String unit;
        private String status;
    }
}
