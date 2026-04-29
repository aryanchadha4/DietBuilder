package com.dietbuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class NutrientReferenceService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode referenceData;

    @Value("${nutrition.protein.muscle-gain.min-g-per-kg:1.6}")
    private double proteinMuscleGainMinPerKg;
    @Value("${nutrition.protein.muscle-gain.max-g-per-kg:2.2}")
    private double proteinMuscleGainMaxPerKg;
    @Value("${nutrition.protein.fat-loss.min-g-per-kg:1.8}")
    private double proteinFatLossMinPerKg;
    @Value("${nutrition.protein.fat-loss.max-g-per-kg:2.4}")
    private double proteinFatLossMaxPerKg;

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("data/nutrient-references.json").getInputStream();
            referenceData = objectMapper.readTree(is);
            log.info("Loaded nutrient reference data with {} nutrients", nutrientsRoot().size());
        } catch (Exception e) {
            log.error("Failed to load nutrient-references.json", e);
            throw new RuntimeException("Cannot start without nutrient reference data", e);
        }
    }

    /**
     * Supports either {@code {"nutrients": { "calcium": {...}, ... }}} or a flat map of nutrient keys at root.
     */
    private JsonNode nutrientsRoot() {
        JsonNode wrapped = referenceData.path("nutrients");
        if (!wrapped.isMissingNode() && !wrapped.isNull() && wrapped.isObject()) {
            return wrapped;
        }
        return referenceData;
    }

    public double getDRI(String nutrient, int age, String sex) {
        JsonNode nutrientNode = nutrientsRoot().path(nutrient);
        if (nutrientNode.isMissingNode()) return 0;
        for (JsonNode group : nutrientNode.path("groups")) {
            if (matchesDemographic(group, age, sex)) return group.path("rda").asDouble(0);
        }
        return 0;
    }

    public double getUL(String nutrient, int age, String sex) {
        JsonNode nutrientNode = nutrientsRoot().path(nutrient);
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
        return nutrientsRoot().path(nutrient).path("unit").asText("mg");
    }

    public Set<String> getAllTrackedNutrients() {
        Set<String> nutrients = new LinkedHashSet<>();
        nutrientsRoot().fieldNames().forEachRemaining(nutrients::add);
        return nutrients;
    }

    public Map<String, NutrientAdequacy> validateAdequacy(Map<String, Double> nutrientTotals, int age, String sex) {
        return validateAdequacy(nutrientTotals, ValidationContext.basic(age, sex));
    }

    public Map<String, NutrientAdequacy> validateAdequacy(Map<String, Double> nutrientTotals, ValidationContext context) {
        Map<String, NutrientAdequacy> results = new LinkedHashMap<>();
        ProteinTargetRange proteinTarget = resolveProteinTarget(context);
        for (String nutrient : getAllTrackedNutrients()) {
            double planned = nutrientTotals.getOrDefault(nutrient, 0.0);
            double rda = getDRI(nutrient, context.getAge(), context.getSex());
            double ul = getUL(nutrient, context.getAge(), context.getSex());
            String unit = getUnit(nutrient);
            String status;
            String targetType = "RDA";
            boolean known = context.getKnownNutrients() == null || context.getKnownNutrients().contains(nutrient);
            if (!known) {
                status = "UNKNOWN";
            } else if ("protein".equals(nutrient) && proteinTarget != null) {
                targetType = "GOAL_RANGE";
                rda = proteinTarget.minGPerDay();
                ul = proteinTarget.maxGPerDay();
                if (planned < rda * 0.5) status = "DEFICIENT";
                else if (planned < rda * 0.8) status = "LOW";
                else if (planned > ul * 1.15) status = "EXCESSIVE";
                else status = "ADEQUATE";
            } else {
                if (rda > 0 && planned < rda * 0.5) status = "DEFICIENT";
                else if (rda > 0 && planned < rda * 0.8) status = "LOW";
                else if (ul < Double.MAX_VALUE && planned > ul) status = "EXCESSIVE";
                else status = "ADEQUATE";
            }
            NutrientAdequacy adequacy = new NutrientAdequacy();
            adequacy.setPlanned(planned);
            adequacy.setRda(rda);
            adequacy.setUl(ul);
            adequacy.setUnit(unit);
            adequacy.setStatus(status);
            adequacy.setTargetType(targetType);
            adequacy.setKnown(known);
            results.put(nutrient, adequacy);
        }
        return results;
    }

    public double computeAdequacyScore(Map<String, NutrientAdequacy> adequacyMap) {
        if (adequacyMap.isEmpty()) return 0;
        List<NutrientAdequacy> known = adequacyMap.values().stream().filter(NutrientAdequacy::isKnown).toList();
        if (known.isEmpty()) return 0;
        long adequate = known.stream().filter(a -> "ADEQUATE".equals(a.getStatus())).count();
        return (double) adequate / known.size() * 100.0;
    }

    public double computeCoveragePercent(Map<String, NutrientAdequacy> adequacyMap) {
        if (adequacyMap.isEmpty()) return 0;
        long known = adequacyMap.values().stream().filter(NutrientAdequacy::isKnown).count();
        return (double) known / adequacyMap.size() * 100.0;
    }

    private ProteinTargetRange resolveProteinTarget(ValidationContext context) {
        if (context == null || context.getWeightKg() <= 0 || context.getGoals() == null || context.getGoals().isEmpty()) {
            return null;
        }
        boolean muscleGain = containsAny(context.getGoals(), "build muscle", "muscle", "strength", "gain weight");
        boolean fatLoss = containsAny(context.getGoals(), "lose weight", "fat loss", "reduce body fat", "cut");
        if (muscleGain) {
            return new ProteinTargetRange(
                    proteinMuscleGainMinPerKg * context.getWeightKg(),
                    proteinMuscleGainMaxPerKg * context.getWeightKg()
            );
        }
        if (fatLoss) {
            return new ProteinTargetRange(
                    proteinFatLossMinPerKg * context.getWeightKg(),
                    proteinFatLossMaxPerKg * context.getWeightKg()
            );
        }
        return null;
    }

    private boolean containsAny(Collection<String> goals, String... needles) {
        if (goals == null || goals.isEmpty()) return false;
        List<String> normalized = new ArrayList<>();
        for (String g : goals) {
            if (g != null) normalized.add(g.toLowerCase());
        }
        for (String n : needles) {
            for (String g : normalized) {
                if (g.contains(n)) return true;
            }
        }
        return false;
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
        private String targetType;
        private boolean known = true;
    }

    @Data
    public static class ValidationContext {
        private int age;
        private String sex;
        private double weightKg;
        private List<String> goals = List.of();
        private Set<String> knownNutrients;

        public static ValidationContext basic(int age, String sex) {
            ValidationContext c = new ValidationContext();
            c.setAge(age);
            c.setSex(sex);
            return c;
        }
    }

    private record ProteinTargetRange(double minGPerDay, double maxGPerDay) {
    }
}
