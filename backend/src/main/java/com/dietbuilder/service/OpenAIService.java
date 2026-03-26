package com.dietbuilder.service;

import com.dietbuilder.model.CulturalFoodGroup;
import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.ExpertSource;
import com.dietbuilder.model.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final NutrientReferenceService nutrientReferenceService;

    @Value("${openai.api.model}")
    private String model;

    @Value("${openai.api.max-tokens}")
    private int maxTokens;

    @Value("${openai.plan.chunk-days:0}")
    private int planChunkDays;

    @Value("${openai.plan.chunk-parallelism:4}")
    private int planChunkParallelism;

    @Value("${openai.plan.evidence-summary-max-chars:400}")
    private int evidenceSummaryMaxChars;

    /**
     * When generating a plan in parallel chunks, identifies one segment (day range) of the overall plan.
     */
    public record ChunkContext(int chunkIndex, int totalChunks, int dayStart, int dayEnd, int totalPlanDays) {
        public static ChunkContext single(int totalPlanDays) {
            return new ChunkContext(1, 1, 1, totalPlanDays, totalPlanDays);
        }

        public int segmentDays() {
            return dayEnd - dayStart + 1;
        }
    }

    private static List<ChunkContext> buildChunkContexts(int totalPlanDays, int chunkDays) {
        if (chunkDays <= 0 || totalPlanDays <= 0) {
            return List.of(ChunkContext.single(totalPlanDays));
        }
        List<ChunkContext> out = new ArrayList<>();
        int day = 1;
        int chunkIndex = 1;
        int totalChunks = (int) Math.ceil((double) totalPlanDays / chunkDays);
        while (day <= totalPlanDays) {
            int end = Math.min(day + chunkDays - 1, totalPlanDays);
            out.add(new ChunkContext(chunkIndex, totalChunks, day, end, totalPlanDays));
            day = end + 1;
            chunkIndex++;
        }
        return out;
    }

    private static String truncateEvidenceText(String text, int maxChars) {
        if (text == null || text.isBlank() || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    /**
     * Caps completion size: short plans need fewer output tokens; long plans use up to configured max.
     */
    private int computeEffectiveMaxTokens(int segmentDays) {
        int scaled = 2048 + Math.max(1, segmentDays) * 1100;
        return Math.min(maxTokens, Math.max(4096, scaled));
    }

    private static final String SYSTEM_PROMPT = """
        You are an expert registered dietitian (RD) and board-certified specialist in sports dietetics (CSSD).
        Create personalized, evidence-based MULTI-DAY dietary plans grounded in authoritative sources:

        AUTHORITATIVE SOURCES (cite by name when relevant):
        - USDA Dietary Guidelines for Americans 2020-2025
        - Institute of Medicine Dietary Reference Intakes (DRIs)
        - WHO Healthy Diet Fact Sheet
        - ISSN Position Stands on protein, nutrient timing, and body composition
        - ADA Standards of Medical Care in Diabetes
        - DASH Study (for hypertension)
        - KDOQI Guidelines (for kidney disease)
        - ACOG Nutrition Guidelines (for pregnancy)

        HARD RULES:
        1. Each food in a meal MUST include an fdcId (USDA FoodData Central ID) when possible, a name,
           quantityGrams, and keyNutrients map with at least energy, protein, fat, carbohydrate, fiber.
        2. Track and report key micronutrients: calcium, iron, magnesium, zinc, vitaminA, vitaminC,
           vitaminD, vitaminE, vitaminK, vitaminB12, folate, potassium, sodium.
        3. For each major dietary claim, provide an evidenceTag with claim, level
           (GUIDELINE_BACKED, META_ANALYSIS, OBSERVATIONAL, or LOW_CONFIDENCE), source, and explanation.
        4. Respect all dietary restrictions and medical information.
        5. CRITICAL CALORIE RULE: Each day's total calories MUST match the DAILY CALORIE TARGET provided
           in the user message. The sum of all meal calories in a day must equal the target (within ±50 kcal).
           Do NOT under-feed the user. If the target is 2500 kcal, each day must total ~2500 kcal.
           Adjust portion sizes to hit the target. Add snacks if meals alone are insufficient.
        6. If one or more cultural cuisines are specified, the plan MUST use foods, ingredients, spices,
           and cooking methods authentic to those culinary traditions. Do not substitute generic Western
           foods where a specified tradition applies. Respect traditional meal structures and flavor profiles.
           If 2+ cuisines are specified, each day MUST include at least 2 different selected cuisines across
           Breakfast/Lunch/Dinner/Snack. A day that uses only one cuisine is INVALID.
        7. Present all measurements (food quantities, height, weight) in the unit system specified
           by the user (metric or imperial).
        8. Account for ALL listed exercise activities when estimating energy expenditure. Each
           strength and cardio entry contributes to total activity level.

        MULTI-DAY PLAN RULES:
        9. Generate the exact number of days requested. Each day MUST have different meals.
        10. NO meal repetition across days unless the user has very limited food options.
        11. Vary ingredients, cuisines, and preparation styles across days.
            If multiple cuisines are selected, mix them within each day (across meals/snacks),
            rather than assigning only one cuisine per day.
        12. Each day MUST include Breakfast, Lunch, Dinner, and at least one Snack.
        13. Provide clear portion sizes and ingredient lists for every meal.
        14. Nutritional targets (calories, macros, micronutrients) must remain consistent across all days.
        15. Each day should have a descriptive label that reflects mixed cuisines when 2+ cuisines are selected
            (e.g., "Day 1 - Mixed: Mediterranean + South Asian", "Day 2 - Mixed: East Asian + Latin").

        RESPONSE FORMAT: Respond ONLY with valid JSON matching this schema:
        {
          "planContent": "Detailed markdown with BMR/TDEE shown, rationale for targets",
          "days": [
            {
              "dayNumber": 1,
              "label": "Day 1 - Mixed: Mediterranean + South Asian",
              "meals": [
                {
                  "name": "Breakfast",
                  "foods": [
                    {
                      "fdcId": "171705",
                      "name": "Chicken breast, grilled",
                      "quantityGrams": 150,
                      "keyNutrients": {
                        "energy": 248, "protein": 46.5, "fat": 5.4, "carbohydrate": 0, "fiber": 0,
                        "calcium": 11, "iron": 1.1, "vitaminD": 0.2
                      }
                    }
                  ],
                  "calories": 420,
                  "proteinGrams": 28.0,
                  "carbsGrams": 22.0,
                  "fatGrams": 24.0
                }
              ],
              "dailyCalories": 2200,
              "macroBreakdown": { "proteinPercent": 30.0, "carbsPercent": 45.0, "fatPercent": 25.0 }
            }
          ],
          "totalDays": 14,
          "dailyCalories": 2200,
          "macroBreakdown": { "proteinPercent": 30.0, "carbsPercent": 45.0, "fatPercent": 25.0 },
          "nutrientAudit": {
            "nutrients": {
              "vitaminD": { "planned": 12.5, "rda": 15.0, "ul": 100.0, "unit": "mcg", "status": "LOW" }
            },
            "adequacyScore": 78.5
          },
          "evidenceTags": [
            {
              "claim": "High protein intake supports muscle protein synthesis",
              "level": "META_ANALYSIS",
              "source": "ISSN Position Stand on Protein",
              "explanation": "Meta-analyses consistently show 1.6-2.2 g/kg/day optimal for resistance-trained individuals"
            }
          ],
          "notes": "Supplement recommendations and additional commentary"
        }
        """;

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String getModelName() {
        return model;
    }

    public DietPlan generateDietPlan(UserProfile profile, List<String> selectedCuisines,
                                     List<CulturalFoodGroup> culturalFoods,
                                     int numDays, List<String> dislikedFoods) {
        return generatePlanWithChunks(profile, selectedCuisines, culturalFoods, numDays, dislikedFoods,
                null, null, Map.of(), false);
    }

    public DietPlan generateDietPlanWithSources(UserProfile profile, List<String> selectedCuisines,
                                                List<CulturalFoodGroup> culturalFoods,
                                                int numDays, List<String> dislikedFoods,
                                                List<ExpertSource> retrievedSources,
                                                List<DietPlan.ConflictNote> conflicts) {
        Map<String, ExpertSource> sourceMap = (retrievedSources == null ? List.<ExpertSource>of() : retrievedSources).stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(ExpertSource::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
        return generatePlanWithChunks(profile, selectedCuisines, culturalFoods, numDays, dislikedFoods,
                retrievedSources, conflicts, sourceMap, true);
    }

    /**
     * Splits large plans into parallel API calls when {@code openai.plan.chunk-days} is set.
     * Cross-chunk meal variety is not coordinated between requests (tradeoff for latency).
     */
    private DietPlan generatePlanWithChunks(UserProfile profile, List<String> selectedCuisines,
                                            List<CulturalFoodGroup> culturalFoods,
                                            int numDays,
                                            List<String> dislikedFoods,
                                            List<ExpertSource> retrievedSources,
                                            List<DietPlan.ConflictNote> conflicts,
                                            Map<String, ExpertSource> sourceMap,
                                            boolean useExpertEvidenceBlock) {
        if (numDays <= 0) {
            throw new IllegalArgumentException("numDays must be positive");
        }
        List<ChunkContext> contexts = buildChunkContexts(numDays, planChunkDays);
        if (contexts.size() == 1) {
            ChunkContext ctx = contexts.get(0);
            String userMessage = useExpertEvidenceBlock
                    ? buildUserMessageWithSources(profile, selectedCuisines, culturalFoods, numDays, dislikedFoods, retrievedSources, conflicts, ctx)
                    : buildUserMessage(profile, selectedCuisines, culturalFoods, dislikedFoods, ctx);
            return callOpenAISingle(userMessage, sourceMap, ctx.segmentDays());
        }
        log.info("openai.plan chunking: parallel_chunks={} chunk_days_setting={} total_days={} (cross-chunk variety is prompt-only)",
                contexts.size(), planChunkDays, numDays);
        int pool = Math.min(Math.max(1, planChunkParallelism), contexts.size());
        ExecutorService executor = Executors.newFixedThreadPool(pool);
        try {
            List<CompletableFuture<DietPlan>> futures = new ArrayList<>();
            for (ChunkContext ctx : contexts) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    String userMessage = useExpertEvidenceBlock
                            ? buildUserMessageWithSources(profile, selectedCuisines, culturalFoods, numDays, dislikedFoods, retrievedSources, conflicts, ctx)
                            : buildUserMessage(profile, selectedCuisines, culturalFoods, dislikedFoods, ctx);
                    return callOpenAISingle(userMessage, sourceMap, ctx.segmentDays());
                }, executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<DietPlan> parts = futures.stream().map(CompletableFuture::join).toList();
            return mergeDietPlans(parts, numDays, sourceMap);
        } finally {
            executor.shutdown();
        }
    }

    public DietPlan generateAdaptedPlan(UserProfile profile, DietPlan previousPlan,
                                         OutcomeTrackingService.OutcomeTrends trends,
                                         LongitudinalAdaptationService.AdaptationAssessment assessment,
                                         List<String> selectedCuisines,
                                         List<CulturalFoodGroup> culturalFoods,
                                         int numDays, List<String> dislikedFoods) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate an ADAPTED diet plan based on the following context.\n\n");
        sb.append(buildUserMessage(profile, selectedCuisines, culturalFoods, numDays, dislikedFoods));

        if (previousPlan != null) {
            sb.append("\n## PREVIOUS PLAN CONTEXT\n");
            sb.append("Previous daily calories: ").append(previousPlan.getDailyCalories()).append("\n");
            if (previousPlan.getMacroBreakdown() != null) {
                sb.append("Previous macros: P").append(String.format("%.0f", previousPlan.getMacroBreakdown().getProteinPercent()))
                  .append("% / C").append(String.format("%.0f", previousPlan.getMacroBreakdown().getCarbsPercent()))
                  .append("% / F").append(String.format("%.0f", previousPlan.getMacroBreakdown().getFatPercent())).append("%\n");
            }
        }

        if (trends != null) {
            sb.append("\n## OUTCOME TRENDS\n");
            sb.append("- Weight change: ").append(String.format("%.1f", trends.getWeightChangeKg())).append(" kg\n");
            sb.append("- Average adherence: ").append(String.format("%.0f", trends.getAvgAdherence())).append("%\n");
            if (trends.getSymptomFrequency() != null && !trends.getSymptomFrequency().isEmpty()) {
                sb.append("- Reported symptoms: ").append(trends.getSymptomFrequency()).append("\n");
            }
        }

        if (assessment != null && assessment.getReasons() != null) {
            sb.append("\n## ADAPTATION REASONS\n");
            for (String reason : assessment.getReasons()) {
                sb.append("- ").append(reason).append("\n");
            }
        }

        sb.append("\nAdapt the plan to address these issues while maintaining safety and nutritional adequacy.");
        return callOpenAISingle(sb.toString(), Map.of(), numDays);
    }

    @lombok.Data
    public static class ReplacementMeal {
        private String slotId;
        private DietPlan.Meal meal;
    }

    public List<ReplacementMeal> generateReplacementMeals(UserProfile profile,
                                                          DietPlan plan,
                                                          List<DietPlan.RemovedMealSlot> removedSlots,
                                                          List<String> rejectedFoods) {
        String userMessage = buildReplacementMealsMessage(profile, plan, removedSlots, rejectedFoods);
        int effectiveMax = computeEffectiveMaxTokens(Math.max(1, removedSlots != null ? removedSlots.size() : 1));
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", effectiveMax,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );
        try {
            String responseJson = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseReplacementMeals(responseJson);
        } catch (Exception e) {
            log.error("OpenAI replacement meal call failed", e);
            throw new RuntimeException("Failed to generate replacement meals: " + e.getMessage(), e);
        }
    }

    public String buildUserMessage(UserProfile profile, List<String> selectedCuisines,
                                    List<CulturalFoodGroup> culturalFoods,
                                    int numDays, List<String> dislikedFoods) {
        return buildUserMessage(profile, selectedCuisines, culturalFoods, dislikedFoods, ChunkContext.single(numDays));
    }

    public String buildUserMessage(UserProfile profile, List<String> selectedCuisines,
                                    List<CulturalFoodGroup> culturalFoods,
                                    List<String> dislikedFoods,
                                    ChunkContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.totalChunks() > 1) {
            sb.append("Create ONE SEGMENT of a multi-day diet plan with ")
                    .append(ctx.segmentDays()).append(" distinct daily variations (days ")
                    .append(ctx.dayStart()).append("–").append(ctx.dayEnd()).append(" of ")
                    .append(ctx.totalPlanDays()).append(" total).\n");
            sb.append("Other segments are generated in parallel; maximize variety within this segment.\n");
        } else {
            sb.append("Create a personalized MULTI-DAY diet plan with ")
                    .append(ctx.segmentDays()).append(" distinct daily variations.\n");
        }
        sb.append("Each day must have completely different meals. Maximize variety in ingredients, cuisines, and preparation styles.\n\n");

        boolean imperial = "IMPERIAL".equalsIgnoreCase(profile.getPreferredUnits());

        sb.append("## Personal Information\n");
        sb.append("- Age: ").append(profile.getAge()).append(" years\n");
        sb.append("- Gender: ").append(profile.getGender()).append("\n");
        if (profile.getRace() != null && !profile.getRace().isBlank())
            sb.append("- Race/Ethnicity: ").append(profile.getRace()).append("\n");

        if (imperial) {
            double totalInches = profile.getHeightCm() / 2.54;
            int feet = (int) (totalInches / 12);
            int inches = (int) Math.round(totalInches % 12);
            double lbs = profile.getWeightKg() / 0.453592;
            sb.append("- Height: ").append(feet).append("'").append(inches).append("\"\n");
            sb.append("- Weight: ").append(String.format("%.0f", lbs)).append(" lbs\n");
            sb.append("- Unit system: IMPERIAL (present all measurements in imperial units)\n");
        } else {
            sb.append("- Height: ").append(profile.getHeightCm()).append(" cm\n");
            sb.append("- Weight: ").append(profile.getWeightKg()).append(" kg\n");
            sb.append("- Unit system: METRIC\n");
        }

        if (profile.getGoals() != null && !profile.getGoals().isEmpty()) {
            sb.append("\n## Goals\n");
            profile.getGoals().forEach(g -> sb.append("- ").append(g).append("\n"));
        }

        sb.append("\n## Exercise Schedule\n");
        if (profile.getStrengthTraining() != null && !profile.getStrengthTraining().isEmpty()) {
            sb.append("### Strength Training\n");
            for (int i = 0; i < profile.getStrengthTraining().size(); i++) {
                UserProfile.ExerciseSchedule st = profile.getStrengthTraining().get(i);
                sb.append("- ").append(st.getType() != null && !st.getType().isBlank() ? st.getType() : "Strength #" + (i + 1))
                  .append(": ").append(st.getDaysPerWeek()).append(" days/week, ")
                  .append(st.getDurationMinutes()).append(" min/session\n");
            }
        } else {
            sb.append("- No strength training\n");
        }
        if (profile.getCardioSchedule() != null && !profile.getCardioSchedule().isEmpty()) {
            sb.append("### Cardio\n");
            for (int i = 0; i < profile.getCardioSchedule().size(); i++) {
                UserProfile.ExerciseSchedule cs = profile.getCardioSchedule().get(i);
                sb.append("- ").append(cs.getType() != null && !cs.getType().isBlank() ? cs.getType() : "Cardio #" + (i + 1))
                  .append(": ").append(cs.getDaysPerWeek()).append(" days/week, ")
                  .append(cs.getDurationMinutes()).append(" min/session\n");
            }
        } else {
            sb.append("- No cardio\n");
        }

        double avgDailyMinutes = 0;
        if (profile.getStrengthTraining() != null)
            avgDailyMinutes += profile.getStrengthTraining().stream().mapToDouble(e -> e.getDaysPerWeek() * e.getDurationMinutes() / 7.0).sum();
        if (profile.getCardioSchedule() != null)
            avgDailyMinutes += profile.getCardioSchedule().stream().mapToDouble(e -> e.getDaysPerWeek() * e.getDurationMinutes() / 7.0).sum();

        double bmr = computeBMR(profile);
        double activityMultiplier = avgDailyMinutes < 15 ? 1.2 : avgDailyMinutes < 30 ? 1.375 : avgDailyMinutes < 60 ? 1.55 : avgDailyMinutes < 90 ? 1.725 : 1.9;
        double tdee = bmr * activityMultiplier;

        double calorieTarget = tdee;
        String targetRationale = "maintenance";
        List<String> goals = profile.getGoals() != null ? profile.getGoals() : List.of();
        boolean wantsLoss = goals.stream().anyMatch(g -> g.toLowerCase().contains("lose weight") || g.toLowerCase().contains("reduce body fat"));
        boolean wantsGain = goals.stream().anyMatch(g -> g.toLowerCase().contains("build muscle") || g.toLowerCase().contains("gain weight"));
        if (wantsLoss && !wantsGain) {
            calorieTarget = tdee - 500;
            targetRationale = "deficit of 500 kcal for fat loss (~1 lb/week)";
        } else if (wantsGain && !wantsLoss) {
            calorieTarget = tdee + 300;
            targetRationale = "surplus of 300 kcal for lean muscle gain";
        } else if (wantsGain && wantsLoss) {
            calorieTarget = tdee - 200;
            targetRationale = "mild deficit of 200 kcal for body recomposition";
        }

        sb.append("\n## DAILY CALORIE TARGET (MANDATORY — DO NOT DEVIATE)\n");
        sb.append(String.format("- BMR (Mifflin-St Jeor): %.0f kcal\n", bmr));
        sb.append(String.format("- Activity multiplier: %.2f (based on %.0f avg daily exercise minutes)\n", activityMultiplier, avgDailyMinutes));
        sb.append(String.format("- TDEE: %.0f kcal\n", tdee));
        sb.append(String.format("- Goal adjustment: %s\n", targetRationale));
        sb.append(String.format("- >>> DAILY CALORIE TARGET: %.0f kcal <<<\n", calorieTarget));
        sb.append("EVERY day in the plan MUST total this amount (±50 kcal). ");
        sb.append("Size portions and add snacks as needed to reach this target. Do NOT under-feed.\n");

        Set<String> trackedNutrients = nutrientReferenceService.getAllTrackedNutrients();
        if (!trackedNutrients.isEmpty()) {
            sb.append("\n## DRI MICRONUTRIENT TARGETS\n");
            String sex = profile.getGender() != null ? profile.getGender().toLowerCase() : "male";
            for (String nutrient : trackedNutrients) {
                double rda = nutrientReferenceService.getDRI(nutrient, profile.getAge(), sex);
                double ul = nutrientReferenceService.getUL(nutrient, profile.getAge(), sex);
                String unit = nutrientReferenceService.getUnit(nutrient);
                String ulStr = ul >= Double.MAX_VALUE ? "none" : String.valueOf((int) ul);
                sb.append(String.format("- %s: RDA=%.1f, UL=%s %s\n", nutrient, rda, ulStr, unit));
            }
        }

        if (profile.getDietaryRestrictions() != null && !profile.getDietaryRestrictions().isEmpty()) {
            sb.append("\n## DIETARY RESTRICTIONS (MUST RESPECT)\n");
            profile.getDietaryRestrictions().forEach(r -> sb.append("- ").append(r).append("\n"));
        }
        if (profile.getMedicalInfo() != null && !profile.getMedicalInfo().isBlank()) {
            sb.append("\n## MEDICAL INFORMATION\n");
            sb.append(profile.getMedicalInfo()).append("\n");
        }

        if (selectedCuisines != null && !selectedCuisines.isEmpty()) {
            sb.append("\n## CULTURAL FOOD PREFERENCES (HARD CONSTRAINT)\n");
            sb.append("Culinary traditions (use authentic foods, ingredients, spices, and methods across the plan; mix within each day when multiple cuisines are selected): ");
            sb.append(String.join(", ", selectedCuisines)).append("\n");
            sb.append("You MUST draw heavily from these traditions throughout the plan.\n\n");
            sb.append("When 2+ cuisines are selected, each day is REQUIRED to include at least 2 different selected cuisines ");
            sb.append("across breakfast/lunch/dinner/snack. A single-cuisine day is invalid.\n");
            sb.append("Use mixed day labels like 'Day X - Mixed: CuisineA + CuisineB'.\n");
            sb.append("Map cuisines at meal level (for example: breakfast CuisineA, lunch CuisineB, dinner CuisineC, snack CuisineA/B).\n\n");

            if (culturalFoods != null && !culturalFoods.isEmpty()) {
                Map<String, List<CulturalFoodGroup>> byCulture = culturalFoods.stream()
                        .collect(Collectors.groupingBy(g ->
                                g.getCulture() != null && !g.getCulture().isBlank() ? g.getCulture() : "unknown"));
                for (Map.Entry<String, List<CulturalFoodGroup>> cultureEntry : byCulture.entrySet()) {
                    sb.append("### ").append(cultureEntry.getKey()).append("\n");
                    Map<String, List<CulturalFoodGroup>> byCategory = cultureEntry.getValue().stream()
                            .collect(Collectors.groupingBy(CulturalFoodGroup::getCategory));
                    for (Map.Entry<String, List<CulturalFoodGroup>> entry : byCategory.entrySet()) {
                        String categoryLabel = entry.getKey().replace("_", " ");
                        categoryLabel = categoryLabel.substring(0, 1).toUpperCase() + categoryLabel.substring(1);
                        sb.append(categoryLabel).append(": ");
                        List<String> foodNames = entry.getValue().stream()
                                .flatMap(g -> g.getFoods().stream())
                                .map(CulturalFoodGroup.FoodEquivalent::getName)
                                .distinct()
                                .collect(Collectors.toList());
                        sb.append(String.join(", ", foodNames)).append("\n");
                    }
                }
            }

            sb.append("\nUse traditional cooking methods and meal structures for these cuisines.\n");
            sb.append("Generic Western substitutions (e.g., 'whole wheat pasta' instead of roti) are NOT acceptable where a listed tradition applies.\n");
        }

        if (dislikedFoods != null && !dislikedFoods.isEmpty()) {
            sb.append("\n## DISLIKED FOODS (MUST EXCLUDE)\n");
            sb.append("The user has rejected the following foods. Do NOT include any of them:\n");
            dislikedFoods.forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("Replace with nutritionally equivalent alternatives.\n");
        }

        if (profile.getAvailableFoods() != null && !profile.getAvailableFoods().isEmpty()) {
            sb.append("\n## Available Foods (prioritize)\n");
            profile.getAvailableFoods().forEach(f -> sb.append("- ").append(f).append("\n"));
        }

        sb.append("\n## PLAN SCOPE\n");
        if (ctx.totalChunks() > 1) {
            sb.append("Generate ONLY ").append(ctx.segmentDays()).append(" days (dayNumber ")
                    .append(ctx.dayStart()).append(" through ").append(ctx.dayEnd()).append(" inclusive). ");
            sb.append("The full plan is ").append(ctx.totalPlanDays()).append(" days; this response covers only this segment.\n");
        } else {
            sb.append("Generate exactly ").append(ctx.segmentDays()).append(" days of meals.\n");
        }
        sb.append(String.format("Each day MUST total approximately %.0f kcal. ", calorieTarget));
        sb.append("Set dailyCalories to this value for every day object.\n");
        sb.append("Provide complete JSON with 'days' array containing ").append(ctx.segmentDays()).append(" day objects, ");
        sb.append("each with meals (foods with fdcId and keyNutrients), dailyCalories, and macroBreakdown. ");
        sb.append("Also include top-level nutrientAudit, evidenceTags, macroBreakdown, and notes.\n");
        if (selectedCuisines != null && selectedCuisines.size() > 1) {
            sb.append("Cuisine compliance check before finalizing JSON: for EACH day, verify meals/snacks represent at least 2 selected cuisines; ");
            sb.append("if not, revise that day before returning output.\n");
        }
        return sb.toString();
    }

    public String buildUserMessageWithSources(UserProfile profile, List<String> selectedCuisines,
                                              List<CulturalFoodGroup> culturalFoods,
                                              int numDays, List<String> dislikedFoods,
                                              List<ExpertSource> retrievedSources,
                                              List<DietPlan.ConflictNote> conflicts) {
        return buildUserMessageWithSources(profile, selectedCuisines, culturalFoods, numDays, dislikedFoods, retrievedSources, conflicts, ChunkContext.single(numDays));
    }

    public String buildUserMessageWithSources(UserProfile profile, List<String> selectedCuisines,
                                              List<CulturalFoodGroup> culturalFoods,
                                              int numDays, List<String> dislikedFoods,
                                              List<ExpertSource> retrievedSources,
                                              List<DietPlan.ConflictNote> conflicts,
                                              ChunkContext ctx) {
        StringBuilder sb = new StringBuilder(buildUserMessage(profile, selectedCuisines, culturalFoods, dislikedFoods, ctx));
        sb.append("\n\n## EXPERT EVIDENCE (USE THESE SOURCES FOR CLAIMS)\n");
        if (retrievedSources == null || retrievedSources.isEmpty()) {
            sb.append("- No high-confidence expert sources retrieved. If you make claims, mark them LOW_CONFIDENCE.\n");
        } else {
            for (ExpertSource source : retrievedSources) {
                sb.append("- [SOURCE:").append(source.getId()).append("] ")
                        .append(source.getTitle()).append(" | type=").append(source.getSourceType())
                        .append(" | year=").append(source.getPublicationDate()).append("\n");
                if (source.getSummary() != null && !source.getSummary().isBlank()) {
                    sb.append("  Summary: ").append(truncateEvidenceText(source.getSummary(), evidenceSummaryMaxChars)).append("\n");
                }
                if (source.getKeyFindings() != null && !source.getKeyFindings().isEmpty()) {
                    String findings = String.join("; ", source.getKeyFindings());
                    sb.append("  Findings: ").append(truncateEvidenceText(findings, evidenceSummaryMaxChars)).append("\n");
                }
            }
        }

        if (conflicts != null && !conflicts.isEmpty()) {
            sb.append("\n## MIXED EVIDENCE NOTES\n");
            for (DietPlan.ConflictNote conflict : conflicts) {
                sb.append("- Topic: ").append(conflict.getTopic()).append(" -> ").append(conflict.getSummary()).append("\n");
                if (conflict.getResolution() != null) {
                    sb.append("  Resolution: ").append(conflict.getResolution()).append("\n");
                }
            }
        }

        sb.append("\nFor each evidenceTag include sourceId (if available). ");
        sb.append("Do not present unsupported claims as definitive; use LOW_CONFIDENCE for unsupported claims.");
        return sb.toString();
    }

    private String buildReplacementMealsMessage(UserProfile profile,
                                                DietPlan plan,
                                                List<DietPlan.RemovedMealSlot> removedSlots,
                                                List<String> rejectedFoods) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate replacement meals ONLY for removed slots in an existing plan.\n");
        sb.append("Do NOT regenerate the full plan. Keep nutrition style and cuisine consistency.\n");
        sb.append("Return ONLY JSON matching this schema:\n");
        sb.append("{\"replacements\":[{\"slotId\":\"slot-1\",\"meal\":{...meal schema...}}]}\n\n");
        sb.append("Use meal schema fields: name, foods[{fdcId,name,quantityGrams,keyNutrients}], calories, proteinGrams, carbsGrams, fatGrams.\n");
        sb.append("Do not include markdown or extra keys.\n\n");
        sb.append("## Profile context\n");
        sb.append("- Age: ").append(profile.getAge()).append("\n");
        sb.append("- Gender: ").append(profile.getGender()).append("\n");
        sb.append("- HeightCm: ").append(profile.getHeightCm()).append("\n");
        sb.append("- WeightKg: ").append(profile.getWeightKg()).append("\n");
        if (profile.getDietaryRestrictions() != null && !profile.getDietaryRestrictions().isEmpty()) {
            sb.append("- Dietary restrictions: ").append(String.join(", ", profile.getDietaryRestrictions())).append("\n");
        }
        if (profile.getMedicalInfo() != null && !profile.getMedicalInfo().isBlank()) {
            sb.append("- Medical info: ").append(profile.getMedicalInfo()).append("\n");
        }
        if (plan.getCuisinePreferences() != null && !plan.getCuisinePreferences().isEmpty()) {
            sb.append("- Cuisines: ").append(String.join(", ", plan.getCuisinePreferences())).append("\n");
        }
        if (rejectedFoods != null && !rejectedFoods.isEmpty()) {
            sb.append("- Exclude foods: ").append(String.join(", ", rejectedFoods)).append("\n");
        }
        sb.append("\n## Removed slots\n");
        for (DietPlan.RemovedMealSlot slot : removedSlots) {
            sb.append("- slotId=").append(slot.getSlotId())
                    .append(", dayIndex=").append(slot.getDayIndex() == null ? "null" : slot.getDayIndex())
                    .append(", mealName=").append(slot.getMealName() == null ? "Meal" : slot.getMealName())
                    .append(", targetCalories=").append(slot.getOriginalMealSnapshot() != null ? slot.getOriginalMealSnapshot().getCalories() : 0)
                    .append("\n");
        }
        sb.append("\n## Existing plan meals (for style consistency)\n");
        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            for (int i = 0; i < plan.getDays().size(); i++) {
                DietPlan.DayPlan day = plan.getDays().get(i);
                sb.append("Day ").append(i).append(": ");
                List<String> names = day.getMeals() == null ? List.of() : day.getMeals().stream().map(DietPlan.Meal::getName).toList();
                sb.append(String.join(", ", names)).append("\n");
            }
        } else {
            List<String> names = plan.getMeals() == null ? List.of() : plan.getMeals().stream().map(DietPlan.Meal::getName).toList();
            sb.append("Single-day meals: ").append(String.join(", ", names)).append("\n");
        }
        sb.append("\nReturn exactly one replacement object per removed slotId.");
        return sb.toString();
    }

    private double computeBMR(UserProfile profile) {
        String sex = profile.getGender() != null ? profile.getGender().toLowerCase() : "male";
        if (sex.startsWith("f")) {
            return 10 * profile.getWeightKg() + 6.25 * profile.getHeightCm() - 5 * profile.getAge() - 161;
        } else {
            return 10 * profile.getWeightKg() + 6.25 * profile.getHeightCm() - 5 * profile.getAge() + 5;
        }
    }

    private DietPlan mergeDietPlans(List<DietPlan> parts, int totalPlanDays, Map<String, ExpertSource> sourceMap) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("empty plan chunks");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        List<DietPlan.DayPlan> allDays = new ArrayList<>();
        StringBuilder planContent = new StringBuilder();
        List<DietPlan.EvidenceTag> allEvidence = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        DietPlan.NutrientAudit mergedAudit = null;
        for (DietPlan p : parts) {
            if (p.getDays() != null) {
                allDays.addAll(p.getDays());
            }
            if (p.getPlanContent() != null && !p.getPlanContent().isBlank()) {
                if (planContent.length() > 0) {
                    planContent.append("\n\n---\n\n");
                }
                planContent.append(p.getPlanContent());
            }
            if (p.getEvidenceTags() != null) {
                allEvidence.addAll(p.getEvidenceTags());
            }
            if (p.getNotes() != null && !p.getNotes().isBlank()) {
                notes.add(p.getNotes());
            }
            if (mergedAudit == null && p.getNutrientAudit() != null) {
                mergedAudit = p.getNutrientAudit();
            }
        }
        allDays.sort(Comparator.comparingInt(DietPlan.DayPlan::getDayNumber));
        DietPlan merged = new DietPlan();
        merged.setPlanContent(planContent.toString());
        merged.setDays(allDays);
        merged.setTotalDays(totalPlanDays);
        if (!allDays.isEmpty()) {
            DietPlan.DayPlan first = allDays.get(0);
            merged.setMeals(first.getMeals());
            merged.setDailyCalories(first.getDailyCalories());
            merged.setMacroBreakdown(first.getMacroBreakdown());
        }
        merged.setEvidenceTags(allEvidence);
        merged.setNutrientAudit(mergedAudit);
        if (!notes.isEmpty()) {
            merged.setNotes(String.join("\n\n", notes));
        }
        if (sourceMap != null && !sourceMap.isEmpty()) {
            merged.setSourceIds(new ArrayList<>(sourceMap.keySet()));
        }
        return merged;
    }

    /** Logs OpenAI usage from the raw response so you can correlate latency with prompt/completion size. */
    private void logOpenAiUsage(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode() || !usage.isObject()) {
                return;
            }
            log.info("openai.plan usage prompt_tokens={} completion_tokens={} total_tokens={}",
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    usage.path("total_tokens").asInt());
        } catch (Exception e) {
            log.debug("Could not parse usage from OpenAI response", e);
        }
    }

    private DietPlan callOpenAISingle(String userMessage, Map<String, ExpertSource> sourceMap, int segmentDays) {
        int effectiveMax = computeEffectiveMaxTokens(segmentDays);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", effectiveMax,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        long t0 = System.currentTimeMillis();
        try {
            String responseJson = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            long chatMs = System.currentTimeMillis() - t0;
            logOpenAiUsage(responseJson);
            long tParseStart = System.currentTimeMillis();
            DietPlan plan = parseResponse(responseJson, sourceMap);
            long parseMs = System.currentTimeMillis() - tParseStart;
            log.info("openai.plan phase=chat_completion_ms={} parse_ms={} effective_max_tokens={}", chatMs, parseMs, effectiveMax);
            return plan;
        } catch (WebClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.error("OpenAI API HTTP {}: {}", e.getStatusCode(), body);
            throw new RuntimeException("OpenAI API error (" + e.getStatusCode() + "): "
                    + (body != null && !body.isBlank() ? body : e.getMessage()), e);
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            throw new RuntimeException("Failed to generate diet plan: " + e.getMessage(), e);
        }
    }

    private List<ReplacementMeal> parseReplacementMeals(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode replacementsNode = parsed.path("replacements");
            if (!replacementsNode.isArray()) {
                throw new RuntimeException("Replacement response missing 'replacements' array");
            }
            List<ReplacementMeal> replacements = new ArrayList<>();
            for (JsonNode rn : replacementsNode) {
                String slotId = rn.path("slotId").asText();
                JsonNode mealNode = rn.path("meal");
                if (slotId == null || slotId.isBlank() || mealNode.isMissingNode()) {
                    continue;
                }
                List<DietPlan.Meal> parsedMeals = parseMeals(objectMapper.createArrayNode().add(mealNode));
                if (parsedMeals.isEmpty()) {
                    continue;
                }
                ReplacementMeal replacement = new ReplacementMeal();
                replacement.setSlotId(slotId);
                replacement.setMeal(parsedMeals.get(0));
                replacements.add(replacement);
            }
            return replacements;
        } catch (Exception e) {
            log.error("Failed to parse replacement meals", e);
            throw new RuntimeException("Failed to parse replacement meals response", e);
        }
    }

    private List<DietPlan.Meal> parseMeals(JsonNode mealsNode) {
        List<DietPlan.Meal> meals = new ArrayList<>();
        for (JsonNode mn : mealsNode) {
            DietPlan.Meal meal = new DietPlan.Meal();
            meal.setName(mn.path("name").asText());
            meal.setCalories(mn.path("calories").asInt());
            meal.setProteinGrams(mn.path("proteinGrams").asDouble());
            meal.setCarbsGrams(mn.path("carbsGrams").asDouble());
            meal.setFatGrams(mn.path("fatGrams").asDouble());

            List<DietPlan.MealFood> foods = new ArrayList<>();
            for (JsonNode fn : mn.path("foods")) {
                if (fn.isTextual()) {
                    DietPlan.MealFood mf = new DietPlan.MealFood();
                    mf.setName(fn.asText());
                    foods.add(mf);
                } else {
                    DietPlan.MealFood mf = new DietPlan.MealFood();
                    mf.setFdcId(fn.path("fdcId").asText(null));
                    mf.setName(fn.path("name").asText());
                    mf.setQuantityGrams(fn.path("quantityGrams").asDouble());
                    Map<String, Double> keyNutrients = new LinkedHashMap<>();
                    JsonNode kn = fn.path("keyNutrients");
                    if (kn.isObject())
                        kn.fieldNames().forEachRemaining(f -> keyNutrients.put(f, kn.path(f).asDouble()));
                    mf.setKeyNutrients(keyNutrients);
                    foods.add(mf);
                }
            }
            meal.setFoods(foods);
            meals.add(meal);
        }
        return meals;
    }

    private DietPlan.MacroBreakdown parseMacroBreakdown(JsonNode macros) {
        DietPlan.MacroBreakdown mb = new DietPlan.MacroBreakdown();
        mb.setProteinPercent(macros.path("proteinPercent").asDouble());
        mb.setCarbsPercent(macros.path("carbsPercent").asDouble());
        mb.setFatPercent(macros.path("fatPercent").asDouble());
        return mb;
    }

    private DietPlan parseResponse(String responseJson, Map<String, ExpertSource> sourceMap) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                String snippet = responseJson.length() > 800 ? responseJson.substring(0, 800) + "…" : responseJson;
                log.error("OpenAI response missing choices: {}", snippet);
                throw new RuntimeException("OpenAI response missing choices (invalid key, rate limit, or API error body).");
            }
            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                log.error("OpenAI returned empty message content");
                throw new RuntimeException("OpenAI returned empty message content.");
            }
            JsonNode p = objectMapper.readTree(content);

            DietPlan dp = new DietPlan();
            dp.setPlanContent(p.path("planContent").asText());
            dp.setDailyCalories(p.path("dailyCalories").asInt());
            dp.setNotes(p.path("notes").asText());
            dp.setTotalDays(p.path("totalDays").asInt(0));

            if (p.has("macroBreakdown")) {
                dp.setMacroBreakdown(parseMacroBreakdown(p.path("macroBreakdown")));
            }

            // Parse multi-day structure
            JsonNode daysNode = p.path("days");
            if (daysNode.isArray() && daysNode.size() > 0) {
                List<DietPlan.DayPlan> days = new ArrayList<>();
                for (JsonNode dayNode : daysNode) {
                    DietPlan.DayPlan day = new DietPlan.DayPlan();
                    day.setDayNumber(dayNode.path("dayNumber").asInt());
                    day.setLabel(dayNode.path("label").asText("Day " + day.getDayNumber()));
                    day.setDailyCalories(dayNode.path("dailyCalories").asInt());
                    if (dayNode.has("macroBreakdown")) {
                        day.setMacroBreakdown(parseMacroBreakdown(dayNode.path("macroBreakdown")));
                    }
                    day.setMeals(parseMeals(dayNode.path("meals")));
                    days.add(day);
                }
                dp.setDays(days);
                dp.setTotalDays(days.size());

                // Populate legacy single-day fields from day 1 for backward compat
                if (!days.isEmpty()) {
                    DietPlan.DayPlan firstDay = days.get(0);
                    dp.setMeals(firstDay.getMeals());
                    if (dp.getDailyCalories() == 0) dp.setDailyCalories(firstDay.getDailyCalories());
                    if (dp.getMacroBreakdown() == null) dp.setMacroBreakdown(firstDay.getMacroBreakdown());
                }
            } else if (p.has("meals")) {
                // Fallback: single-day response (legacy compat)
                dp.setMeals(parseMeals(p.path("meals")));
            }

            // Nutrient audit
            JsonNode auditNode = p.path("nutrientAudit");
            if (!auditNode.isMissingNode()) {
                DietPlan.NutrientAudit audit = new DietPlan.NutrientAudit();
                audit.setAdequacyScore(auditNode.path("adequacyScore").asDouble());
                Map<String, DietPlan.NutrientStatus> nutrients = new LinkedHashMap<>();
                JsonNode nutrNode = auditNode.path("nutrients");
                if (nutrNode.isObject()) {
                    nutrNode.fieldNames().forEachRemaining(name -> {
                        JsonNode ns = nutrNode.path(name);
                        DietPlan.NutrientStatus status = new DietPlan.NutrientStatus();
                        status.setPlanned(ns.path("planned").asDouble());
                        status.setRda(ns.path("rda").asDouble());
                        status.setUl(ns.path("ul").asDouble());
                        status.setUnit(ns.path("unit").asText());
                        status.setStatus(ns.path("status").asText());
                        nutrients.put(name, status);
                    });
                }
                audit.setNutrients(nutrients);
                dp.setNutrientAudit(audit);
            }

            // Evidence tags
            List<DietPlan.EvidenceTag> evidenceTags = new ArrayList<>();
            for (JsonNode et : p.path("evidenceTags")) {
                DietPlan.EvidenceTag tag = new DietPlan.EvidenceTag();
                tag.setClaim(et.path("claim").asText());
                try {
                    tag.setLevel(DietPlan.EvidenceLevel.valueOf(et.path("level").asText()));
                } catch (IllegalArgumentException e) {
                    tag.setLevel(DietPlan.EvidenceLevel.LOW_CONFIDENCE);
                }
                tag.setSource(et.path("source").asText());
                tag.setExplanation(et.path("explanation").asText());
                String sourceId = et.path("sourceId").asText(null);
                if (sourceId == null || sourceId.isBlank()) {
                    sourceId = extractSourceId(tag.getSource());
                }
                if (sourceId != null && !sourceId.isBlank()) {
                    tag.setSourceId(sourceId);
                    ExpertSource source = sourceMap.get(sourceId);
                    if (source != null) {
                        tag.setDoi(source.getDoi());
                        tag.setUrl(source.getUrl());
                        tag.setSimpleSummary(source.getSummary());
                        tag.setCitationText(formatCitation(source));
                    }
                }
                tag.setRelevanceScore(et.path("relevanceScore").asDouble(0.0));
                if (tag.getSimpleSummary() == null || tag.getSimpleSummary().isBlank()) {
                    tag.setSimpleSummary(et.path("simpleSummary").asText(null));
                }
                evidenceTags.add(tag);
            }
            dp.setEvidenceTags(evidenceTags);
            if (!sourceMap.isEmpty()) {
                dp.setSourceIds(new ArrayList<>(sourceMap.keySet()));
            }

            return dp;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
            throw new RuntimeException("Failed to parse diet plan response", e);
        }
    }

    private String extractSourceId(String value) {
        if (value == null) return null;
        int start = value.indexOf("[SOURCE:");
        if (start < 0) return null;
        int end = value.indexOf("]", start);
        if (end < 0) return null;
        return value.substring(start + 8, end).trim();
    }

    private String formatCitation(ExpertSource source) {
        StringBuilder sb = new StringBuilder();
        if (source.getAuthors() != null && !source.getAuthors().isBlank()) sb.append(source.getAuthors()).append(". ");
        if (source.getTitle() != null && !source.getTitle().isBlank()) sb.append(source.getTitle()).append(". ");
        if (source.getJournal() != null && !source.getJournal().isBlank()) sb.append(source.getJournal()).append(". ");
        if (source.getPublicationDate() != null && !source.getPublicationDate().isBlank()) sb.append(source.getPublicationDate()).append(". ");
        if (source.getDoi() != null && !source.getDoi().isBlank()) sb.append("DOI: ").append(source.getDoi());
        return sb.toString().trim();
    }
}
