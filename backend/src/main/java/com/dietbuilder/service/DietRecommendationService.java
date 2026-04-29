package com.dietbuilder.service;

import com.dietbuilder.model.CulturalFoodGroup;
import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.ExpertSource;
import com.dietbuilder.model.User;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.CulturalFoodGroupRepository;
import com.dietbuilder.repository.DietPlanRepository;
import com.dietbuilder.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DietRecommendationService {

    private final UserProfileRepository profileRepository;
    private final DietPlanRepository dietPlanRepository;
    private final OpenAIService openAIService;
    private final AuthService authService;
    private final CulturalFoodGroupRepository culturalFoodGroupRepository;
    private final SafetyGuardrailService safetyGuardrailService;
    private final NutrientReferenceService nutrientReferenceService;
    private final RecommendationAuditService auditService;
    private final FoodPreferenceService foodPreferenceService;
    private final ExpertKnowledgeService expertKnowledgeService;
    private final ConflictResolutionService conflictResolutionService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return authService.findByUsername(auth.getName());
    }

    public UserProfile saveProfile(UserProfile profile) {
        User user = getCurrentUser();
        profile.setUserId(user.getId());
        return profileRepository.save(profile);
    }

    public UserProfile getProfile(String id) {
        UserProfile profile = profileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + id));
        User user = getCurrentUser();
        if (!user.getId().equals(profile.getUserId())) {
            throw new RuntimeException("Access denied");
        }
        return profile;
    }

    public List<UserProfile> getAllProfiles() {
        User user = getCurrentUser();
        return profileRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public UserProfile updateProfile(String id, UserProfile updates) {
        UserProfile existing = getProfile(id);
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getAge() > 0) existing.setAge(updates.getAge());
        if (updates.getGender() != null) existing.setGender(updates.getGender());
        if (updates.getRace() != null) existing.setRace(updates.getRace());
        if (updates.getHeightCm() > 0) existing.setHeightCm(updates.getHeightCm());
        if (updates.getWeightKg() > 0) existing.setWeightKg(updates.getWeightKg());
        if (updates.getPreferredUnits() != null) existing.setPreferredUnits(updates.getPreferredUnits());
        if (updates.getStrengthTraining() != null) existing.setStrengthTraining(updates.getStrengthTraining());
        if (updates.getCardioSchedule() != null) existing.setCardioSchedule(updates.getCardioSchedule());
        if (updates.getDietaryRestrictions() != null) existing.setDietaryRestrictions(updates.getDietaryRestrictions());
        if (updates.getMedicalInfo() != null) existing.setMedicalInfo(updates.getMedicalInfo());
        if (updates.getGoals() != null) existing.setGoals(updates.getGoals());
        if (updates.getAvailableFoods() != null) existing.setAvailableFoods(updates.getAvailableFoods());
        return profileRepository.save(existing);
    }

    private List<String> normalizeCuisineList(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String c : raw) {
            if (c != null && !c.isBlank()) seen.add(c.trim());
        }
        return new ArrayList<>(seen);
    }

    private List<CulturalFoodGroup> loadCulturalFoodGroups(List<String> cuisines) {
        List<String> normalized = normalizeCuisineList(cuisines);
        if (normalized.isEmpty()) return List.of();
        List<CulturalFoodGroup> merged = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (String culture : normalized) {
            for (CulturalFoodGroup g : culturalFoodGroupRepository.findByCulture(culture)) {
                if (g.getId() != null) {
                    if (seenIds.add(g.getId())) merged.add(g);
                } else {
                    merged.add(g);
                }
            }
        }
        log.info("Loaded {} cultural food groups for cuisines {}", merged.size(), normalized);
        return merged;
    }

    public DietPlan generateRecommendation(String profileId, int numDays, List<String> dislikedFoods,
                                           List<String> cuisines) {
        UserProfile profile = getProfile(profileId);
        long startTime = System.currentTimeMillis();
        log.info("recommendation.start profileId={} numDays={} cuisines={}", profileId, numDays,
                cuisines == null ? List.of() : cuisines);

        User user = getCurrentUser();
        List<String> storedDislikes = foodPreferenceService.getActiveDislikedFoodNames(user.getId());
        List<String> allDisliked = new ArrayList<>(storedDislikes);
        if (dislikedFoods != null) {
            for (String food : dislikedFoods) {
                if (!allDisliked.contains(food)) allDisliked.add(food);
            }
        }
        log.info("Excluding {} disliked foods from plan generation", allDisliked.size());

        log.info("Running pre-generation safety checks for profile {}", profileId);
        long tPreCheckStart = System.currentTimeMillis();
        SafetyGuardrailService.SafetyCheckResult preCheck = safetyGuardrailService.runPreChecks(profile);
        long preCheckMs = System.currentTimeMillis() - tPreCheckStart;

        List<String> cuisineList = normalizeCuisineList(cuisines);

        if (preCheck.isBlocked()) {
            log.warn("Pre-check blocked plan generation for profile {}", profileId);
            DietPlan blockedPlan = new DietPlan();
            blockedPlan.setProfileId(profileId);
            blockedPlan.setCuisinePreferences(new ArrayList<>(cuisineList));
            blockedPlan.setSafetyAlerts(preCheck.getAlerts());
            blockedPlan.setSafetyCleared(false);
            blockedPlan.setNotes("Plan generation was blocked due to safety concerns. " +
                    "Please consult your healthcare provider before proceeding.");
            safetyGuardrailService.persistAlerts(preCheck.getAlerts(), profileId, null);
            return dietPlanRepository.save(blockedPlan);
        }

        List<CulturalFoodGroup> culturalFoods = loadCulturalFoodGroups(cuisineList);

        long tRetrievalStart = System.currentTimeMillis();
        ExpertKnowledgeService.RetrievalResult retrievalResult = expertKnowledgeService.retrieveForProfile(profile, cuisineList, 15);
        long retrievalMs = System.currentTimeMillis() - tRetrievalStart;
        List<ExpertSource> sources = retrievalResult.sources();
        long tConflictStart = System.currentTimeMillis();
        List<DietPlan.ConflictNote> conflicts = conflictResolutionService.resolve(sources);
        long conflictMs = System.currentTimeMillis() - tConflictStart;

        String ratingFeedbackContext = buildRatingFeedbackContext(profileId);

        log.info("Generating {}-day diet plan via OpenAI for profile {}", numDays, profileId);
        long tOpenAiStart = System.currentTimeMillis();
        DietPlan plan = openAIService.generateDietPlanWithSources(profile, cuisineList, culturalFoods, numDays, allDisliked, sources, conflicts, ratingFeedbackContext);
        CulturalValidationResult culturalValidation = validateCulturalCoherence(plan, cuisineList, culturalFoods);
        if (!culturalValidation.valid()) {
            log.warn("recommendation.culture_validation failed profileId={} violations={}", profileId, culturalValidation.violations());
            String retryConstraints = buildCulturalRetryConstraints(cuisineList, culturalValidation.violations());
            String combinedRetry = combineAdditionalHardConstraints(ratingFeedbackContext, retryConstraints);
            DietPlan retriedPlan = openAIService.generateDietPlanWithSources(
                    profile, cuisineList, culturalFoods, numDays, allDisliked, sources, conflicts, combinedRetry);
            CulturalValidationResult retryValidation = validateCulturalCoherence(retriedPlan, cuisineList, culturalFoods);
            if (retryValidation.valid()) {
                log.info("recommendation.culture_validation retry_succeeded profileId={}", profileId);
                plan = retriedPlan;
            } else {
                log.warn("recommendation.culture_validation retry_failed profileId={} violations={}", profileId, retryValidation.violations());
                plan = retriedPlan;
                String warning = "Cultural coherence warning: one or more meals may mix cuisines or be off-cuisine. "
                        + "Validation issues: " + String.join("; ", retryValidation.violations());
                plan.setNotes(plan.getNotes() == null || plan.getNotes().isBlank()
                        ? warning
                        : plan.getNotes() + "\n\n" + warning);
            }
        } else {
            log.info("recommendation.culture_validation passed profileId={}", profileId);
        }
        long openAiMs = System.currentTimeMillis() - tOpenAiStart;
        plan.setProfileId(profileId);
        plan.setCuisinePreferences(new ArrayList<>(cuisineList));
        plan.setSourceIds(sources.stream().map(ExpertSource::getId).toList());
        plan.setConflictNotes(conflicts);
        plan.setEvidenceConfidenceScore(computeConfidenceScore(retrievalResult.rankedSources(), plan));
        plan.setConfidenceStatement(buildConfidenceStatement(sources, plan.getEvidenceConfidenceScore()));

        if (sources.isEmpty()) {
            if (plan.getEvidenceTags() != null) {
                plan.getEvidenceTags().forEach(tag -> tag.setLevel(DietPlan.EvidenceLevel.LOW_CONFIDENCE));
            }
            plan.setConfidenceStatement("Limited expert evidence was retrieved for this profile. Recommendations are lower confidence.");
        }

        long tAuditStart = System.currentTimeMillis();
        if (plan.getNutrientAudit() == null) {
            log.info("Running server-side nutrient audit");
            runNutrientAudit(plan, profile);
        }
        long auditMs = System.currentTimeMillis() - tAuditStart;

        long tPostCheckStart = System.currentTimeMillis();
        SafetyGuardrailService.SafetyCheckResult postCheck = safetyGuardrailService.runPostChecks(plan, profile);
        long postCheckMs = System.currentTimeMillis() - tPostCheckStart;
        List<DietPlan.SafetyAlert> allAlerts = new ArrayList<>(preCheck.getAlerts());
        allAlerts.addAll(postCheck.getAlerts());
        plan.setSafetyAlerts(allAlerts);
        plan.setSafetyCleared(!postCheck.isBlocked());

        long tSaveStart = System.currentTimeMillis();
        DietPlan saved = dietPlanRepository.save(plan);
        long saveMs = System.currentTimeMillis() - tSaveStart;

        safetyGuardrailService.persistAlerts(allAlerts, profileId, saved.getId());

        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("recommendation.done profileId={} planId={} total_ms={} pre_check_ms={} rag_retrieval_ms={} conflict_resolve_ms={} openai_total_ms={} nutrient_audit_ms={} post_check_ms={} save_ms={}",
                profileId, saved.getId(), latencyMs, preCheckMs, retrievalMs, conflictMs, openAiMs, auditMs, postCheckMs, saveMs);
        List<String> safetyChecks = List.of("pre_generation_safety", "post_generation_safety");
        List<String> postSteps = List.of("nutrient_audit", "safety_post_check", "alert_persistence");
        String userMessage = openAIService.buildUserMessage(profile, cuisineList, culturalFoods, numDays, allDisliked);
        auditService.logRecommendation(profileId, saved.getId(), profile, cuisineList,
                openAIService.getModelName(), openAIService.getSystemPrompt(),
                userMessage, safetyChecks, postSteps, latencyMs,
                plan.getSourceIds(),
                plan.getEvidenceTags() == null ? 0 : (int) plan.getEvidenceTags().stream().filter(t -> t.getSourceId() != null && !t.getSourceId().isBlank()).count(),
                retrievalResult.avgRelevance(),
                retrievalResult.retrievalQuery());

        return saved;
    }

    private double computeConfidenceScore(List<SourceRankingService.RankedSource> rankedSources, DietPlan plan) {
        if (rankedSources == null || rankedSources.isEmpty()) {
            return 0.25;
        }
        double sourceQuality = rankedSources.stream().mapToDouble(SourceRankingService.RankedSource::compositeScore).average().orElse(0.3);
        double citedRatio = 0.0;
        if (plan.getEvidenceTags() != null && !plan.getEvidenceTags().isEmpty()) {
            long cited = plan.getEvidenceTags().stream().filter(t -> t.getSourceId() != null && !t.getSourceId().isBlank()).count();
            citedRatio = (double) cited / plan.getEvidenceTags().size();
        }
        return Math.min(1.0, (sourceQuality * 0.8) + (citedRatio * 0.2));
    }

    private String buildConfidenceStatement(List<ExpertSource> sources, double score) {
        if (sources == null || sources.isEmpty()) {
            return "No strong expert sources were retrieved. Treat recommendations as lower confidence.";
        }
        if (score >= 0.7) {
            return "Recommendations are strongly grounded in high-quality expert evidence.";
        }
        if (score >= 0.4) {
            return "Recommendations are moderately supported by expert evidence with some uncertainty.";
        }
        return "Recommendations have mixed or limited supporting evidence and should be interpreted cautiously.";
    }

    private void runNutrientAudit(DietPlan plan, UserProfile profile) {
        java.util.Map<String, Double> nutrientTotals = new java.util.LinkedHashMap<>();
        if (plan.getMeals() != null) {
            for (DietPlan.Meal meal : plan.getMeals()) {
                if (meal.getFoods() != null) {
                    for (DietPlan.MealFood food : meal.getFoods()) {
                        if (food.getKeyNutrients() != null) {
                            for (var e : food.getKeyNutrients().entrySet()) {
                                nutrientTotals.merge(e.getKey(), e.getValue(), Double::sum);
                            }
                        }
                    }
                }
            }
        }
        String sex = profile.getGender() != null ? profile.getGender().toLowerCase() : "male";
        java.util.Map<String, NutrientReferenceService.NutrientAdequacy> adequacyMap =
                nutrientReferenceService.validateAdequacy(nutrientTotals, profile.getAge(), sex);
        double score = nutrientReferenceService.computeAdequacyScore(adequacyMap);

        DietPlan.NutrientAudit audit = new DietPlan.NutrientAudit();
        java.util.Map<String, DietPlan.NutrientStatus> auditNutrients = new java.util.LinkedHashMap<>();
        for (var e : adequacyMap.entrySet()) {
            DietPlan.NutrientStatus ns = new DietPlan.NutrientStatus();
            ns.setPlanned(e.getValue().getPlanned());
            ns.setRda(e.getValue().getRda());
            ns.setUl(e.getValue().getUl());
            ns.setUnit(e.getValue().getUnit());
            ns.setStatus(e.getValue().getStatus());
            auditNutrients.put(e.getKey(), ns);
        }
        audit.setNutrients(auditNutrients);
        audit.setAdequacyScore(score);
        plan.setNutrientAudit(audit);
    }

    public DietPlan regeneratePlan(String profileId,
                                    com.dietbuilder.controller.DietPlanController.RegenerateRequest request) {
        rememberRejectedFoods(request.getRejectedFoods(), "Rejected from plan regeneration", request.getParentPlanId());

        // Determine number of days from parent plan or request
        int numDays = request.getDays() > 0 ? request.getDays() : 14;
        if (request.getParentPlanId() != null) {
            dietPlanRepository.findById(request.getParentPlanId()).ifPresent(parent -> {
                if (parent.getTotalDays() > 0 && request.getDays() <= 0) {
                    // Use parent's day count as default
                }
            });
        }

        List<String> cuisines = normalizeCuisineList(request.getCuisines());
        if (cuisines.isEmpty() && request.getParentPlanId() != null) {
            cuisines = dietPlanRepository.findById(request.getParentPlanId())
                    .map(p -> normalizeCuisineList(p.getCuisinePreferences()))
                    .orElse(List.of());
        }

        // Generate new plan (disliked foods auto-loaded from preferences)
        DietPlan newPlan = generateRecommendation(profileId, numDays, request.getRejectedFoods(), cuisines);

        // Link to parent plan
        if (request.getParentPlanId() != null) {
            newPlan.setParentPlanId(request.getParentPlanId());
            dietPlanRepository.findById(request.getParentPlanId()).ifPresent(parent ->
                    newPlan.setVersion(parent.getVersion() + 1));
            dietPlanRepository.save(newPlan);
        }

        return newPlan;
    }

    public DietPlan regenerateRemovedMeals(String planId,
                                           com.dietbuilder.controller.DietPlanController.RegenerateRemovedRequest request) {
        DietPlan plan = dietPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        UserProfile profile = getProfile(plan.getProfileId());
        List<DietPlan.RemovedMealSlot> removedSlots = plan.getRemovedMealSlots() == null
                ? new ArrayList<>()
                : new ArrayList<>(plan.getRemovedMealSlots());
        if (removedSlots.isEmpty()) {
            throw new IllegalArgumentException("No removed meals to regenerate");
        }

        List<String> rejectedFoods = request != null ? request.getRejectedFoods() : null;
        rememberRejectedFoods(rejectedFoods, "Rejected while regenerating removed meals", planId);
        List<OpenAIService.ReplacementMeal> replacements = openAIService.generateReplacementMeals(
                profile, plan, removedSlots, rejectedFoods);
        if (replacements.size() != removedSlots.size()) {
            throw new IllegalStateException("Replacement count mismatch. Expected " + removedSlots.size() +
                    " but got " + replacements.size());
        }

        Map<String, DietPlan.Meal> replacementBySlotId = replacements.stream()
                .collect(java.util.stream.Collectors.toMap(OpenAIService.ReplacementMeal::getSlotId,
                        OpenAIService.ReplacementMeal::getMeal, (a, b) -> a));

        for (DietPlan.RemovedMealSlot slot : removedSlots) {
            DietPlan.Meal replacement = replacementBySlotId.get(slot.getSlotId());
            if (replacement == null) {
                throw new IllegalStateException("Missing replacement for slot " + slot.getSlotId());
            }
            insertReplacementMeal(plan, slot, replacement);
        }

        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            for (DietPlan.DayPlan day : plan.getDays()) {
                recomputeDayPlan(day);
            }
            recomputeMultiDayPlanAggregates(plan);
        } else {
            recomputeLegacyPlanFromMeals(plan);
        }
        plan.setNutrientAudit(null);
        plan.setRemovedMealSlots(new ArrayList<>());
        return dietPlanRepository.save(plan);
    }

    public List<DietPlan> getDietPlans(String profileId) {
        getProfile(profileId);
        return dietPlanRepository.findByProfileIdOrderByCreatedAtDesc(profileId);
    }

    private static final int MAX_RATING_FEEDBACK_CHARS = 2000;
    private static final int RATED_PLANS_FOR_CONTEXT = 15;
    private static final int MAX_FEEDBACK_NAMES_PER_BUCKET = 25;
    private static final int MAX_LOW_RATING_NOTES = 5;

    /**
     * Hard-deletes a plan after verifying the current user owns its profile.
     */
    public void deletePlan(String planId) {
        DietPlan plan = dietPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        getProfile(plan.getProfileId());
        dietPlanRepository.deleteById(planId);
    }

    /**
     * Saves a 1-5 star rating (and optional feedback) for a plan.
     */
    public DietPlan ratePlan(String planId, int rating, String feedback) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        DietPlan plan = dietPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        getProfile(plan.getProfileId());
        plan.setUserRating(rating);
        if (feedback != null) {
            String f = feedback.trim();
            if (f.length() > MAX_RATING_FEEDBACK_CHARS) {
                f = f.substring(0, MAX_RATING_FEEDBACK_CHARS);
            }
            plan.setRatingFeedback(f.isEmpty() ? null : f);
        } else {
            plan.setRatingFeedback(null);
        }
        plan.setRatedAt(Instant.now());
        return dietPlanRepository.save(plan);
    }

    /**
     * Builds a compact block injected into plan generation prompts from recent rated plans for this profile.
     */
    private String buildRatingFeedbackContext(String profileId) {
        List<DietPlan> rated = dietPlanRepository.findByProfileIdAndUserRatingIsNotNullOrderByRatedAtDesc(
                profileId, PageRequest.of(0, RATED_PLANS_FOR_CONTEXT));
        if (rated.isEmpty()) {
            return null;
        }
        double avg = rated.stream()
                .mapToInt(p -> p.getUserRating() != null ? p.getUserRating() : 0)
                .average().orElse(0);
        LinkedHashSet<String> preferred = new LinkedHashSet<>();
        LinkedHashSet<String> avoided = new LinkedHashSet<>();
        List<String> lowRatingNotes = new ArrayList<>();
        for (DietPlan p : rated) {
            Integer r = p.getUserRating();
            if (r == null) {
                continue;
            }
            if (r >= 4) {
                collectMealAndFoodNamesForFeedback(p, preferred);
            } else if (r <= 2) {
                collectMealAndFoodNamesForFeedback(p, avoided);
                if (p.getRatingFeedback() != null && !p.getRatingFeedback().isBlank()) {
                    String note = p.getRatingFeedback().trim().replaceAll("\\s+", " ");
                    if (note.length() > 120) {
                        note = note.substring(0, 117) + "...";
                    }
                    lowRatingNotes.add(note);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("USER PLAN RATING FEEDBACK (from recent saved plans for this profile; soft preferences only — ")
                .append("never override medical needs, dietary restrictions, calorie targets, or safety rules).\n");
        sb.append(String.format("- Recent rated plans sampled: %d; average rating (1-5): %.1f%n", rated.size(), avg));
        if (!preferred.isEmpty()) {
            sb.append("- User seemed to enjoy these meals/foods (from highly rated plans — prefer similar ideas and variety, not boring repetition): ");
            sb.append(String.join(", ", firstN(preferred, MAX_FEEDBACK_NAMES_PER_BUCKET)));
            sb.append("\n");
        }
        if (!avoided.isEmpty()) {
            sb.append("- Often appeared in poorly rated plans (avoid repeating these where reasonable alternatives exist): ");
            sb.append(String.join(", ", firstN(avoided, MAX_FEEDBACK_NAMES_PER_BUCKET)));
            sb.append("\n");
        }
        if (!lowRatingNotes.isEmpty()) {
            int n = Math.min(MAX_LOW_RATING_NOTES, lowRatingNotes.size());
            sb.append("- Notes from low ratings: ");
            sb.append(String.join(" | ", lowRatingNotes.subList(0, n)));
            sb.append("\n");
        }
        sb.append("Treat the above as guidance alongside disliked-food exclusions and expert evidence.\n");
        return sb.toString();
    }

    private static void collectMealAndFoodNamesForFeedback(DietPlan plan, Set<String> out) {
        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            for (DietPlan.DayPlan day : plan.getDays()) {
                if (day == null || day.getMeals() == null) {
                    continue;
                }
                for (DietPlan.Meal m : day.getMeals()) {
                    addMealFoodNames(m, out);
                }
            }
        } else if (plan.getMeals() != null) {
            for (DietPlan.Meal m : plan.getMeals()) {
                addMealFoodNames(m, out);
            }
        }
    }

    private static void addMealFoodNames(DietPlan.Meal m, Set<String> out) {
        if (m == null) {
            return;
        }
        if (m.getName() != null && !m.getName().isBlank()) {
            out.add(m.getName().trim());
        }
        if (m.getFoods() != null) {
            for (DietPlan.MealFood f : m.getFoods()) {
                if (f != null && f.getName() != null && !f.getName().isBlank()) {
                    out.add(f.getName().trim());
                }
            }
        }
    }

    private static List<String> firstN(Set<String> source, int max) {
        List<String> list = new ArrayList<>(source);
        if (list.size() <= max) {
            return list;
        }
        return list.subList(0, max);
    }

    private static String combineAdditionalHardConstraints(String first, String second) {
        boolean a = first != null && !first.isBlank();
        boolean b = second != null && !second.isBlank();
        if (a && b) {
            return first + "\n\n" + second;
        }
        if (a) {
            return first;
        }
        if (b) {
            return second;
        }
        return null;
    }

    public GroceryListResult generateGroceryList(String planId) {
        DietPlan plan = dietPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        // Access check: reuses existing profile ownership validation.
        getProfile(plan.getProfileId());

        Map<String, String> dedupedFoods = new HashMap<>();
        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            for (DietPlan.DayPlan day : plan.getDays()) {
                collectMealFoods(day.getMeals(), dedupedFoods);
            }
        } else {
            collectMealFoods(plan.getMeals(), dedupedFoods);
        }

        List<String> foods = dedupedFoods.values().stream()
                .sorted(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)))
                .toList();
        return new GroceryListResult(plan.getId(), foods);
    }

    private void collectMealFoods(List<DietPlan.Meal> meals, Map<String, String> dedupedFoods) {
        if (meals == null) return;
        for (DietPlan.Meal meal : meals) {
            if (meal == null || meal.getFoods() == null) continue;
            for (DietPlan.MealFood food : meal.getFoods()) {
                if (food == null || food.getName() == null) continue;
                String displayName = food.getName().trim();
                if (displayName.isBlank()) continue;
                String normalized = normalizeFoodName(displayName);
                dedupedFoods.putIfAbsent(normalized, displayName);
            }
        }
    }

    private String normalizeFoodName(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record GroceryListResult(String planId, List<String> foods) {}

    public List<DietPlan> getAllDietPlans() {
        User user = getCurrentUser();
        List<UserProfile> profiles = profileRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<String> profileIds = profiles.stream().map(UserProfile::getId).toList();
        if (profileIds.isEmpty()) return List.of();
        return dietPlanRepository.findAll().stream()
                .filter(p -> profileIds.contains(p.getProfileId()))
                .toList();
    }

    /**
     * Removes one meal from a saved plan (multi-day or legacy single-day). Recomputes day and plan-level
     * calories and macro breakdowns; clears nutrient audit (stale after edit).
     */
    public DietPlan removeMealFromPlan(String planId, Integer dayIndex, int mealIndex) {
        DietPlan plan = dietPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        getProfile(plan.getProfileId());
        if (plan.getRemovedMealSlots() == null) {
            plan.setRemovedMealSlots(new ArrayList<>());
        }

        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            int dIdx = dayIndex != null ? dayIndex : 0;
            if (dIdx < 0 || dIdx >= plan.getDays().size()) {
                throw new IllegalArgumentException("Invalid dayIndex: " + dayIndex);
            }
            DietPlan.DayPlan day = plan.getDays().get(dIdx);
            if (day.getMeals() == null || mealIndex < 0 || mealIndex >= day.getMeals().size()) {
                throw new IllegalArgumentException("Invalid mealIndex: " + mealIndex);
            }
            DietPlan.Meal removedMeal = day.getMeals().get(mealIndex);
            rememberRejectedMeal(removedMeal, planId);
            plan.getRemovedMealSlots().add(buildRemovedMealSlot(dIdx, mealIndex, removedMeal));
            day.getMeals().remove(mealIndex);
            recomputeDayPlan(day);
            recomputeMultiDayPlanAggregates(plan);
        } else {
            if (plan.getMeals() == null || mealIndex < 0 || mealIndex >= plan.getMeals().size()) {
                throw new IllegalArgumentException("Invalid mealIndex: " + mealIndex);
            }
            DietPlan.Meal removedMeal = plan.getMeals().get(mealIndex);
            rememberRejectedMeal(removedMeal, planId);
            plan.getRemovedMealSlots().add(buildRemovedMealSlot(null, mealIndex, removedMeal));
            plan.getMeals().remove(mealIndex);
            recomputeLegacyPlanFromMeals(plan);
        }

        plan.setNutrientAudit(null);
        return dietPlanRepository.save(plan);
    }

    private static DietPlan.RemovedMealSlot buildRemovedMealSlot(Integer dayIndex, int mealIndex, DietPlan.Meal removedMeal) {
        DietPlan.RemovedMealSlot slot = new DietPlan.RemovedMealSlot();
        slot.setSlotId(UUID.randomUUID().toString());
        slot.setDayIndex(dayIndex);
        slot.setOriginalMealIndex(mealIndex);
        slot.setMealName(removedMeal.getName());
        slot.setRemovedAt(java.time.Instant.now());
        DietPlan.Meal snapshot = new DietPlan.Meal();
        snapshot.setName(removedMeal.getName());
        snapshot.setFoods(removedMeal.getFoods() == null ? new ArrayList<>() : new ArrayList<>(removedMeal.getFoods()));
        snapshot.setCalories(removedMeal.getCalories());
        snapshot.setProteinGrams(removedMeal.getProteinGrams());
        snapshot.setCarbsGrams(removedMeal.getCarbsGrams());
        snapshot.setFatGrams(removedMeal.getFatGrams());
        snapshot.setFiberGrams(removedMeal.getFiberGrams());
        snapshot.setMicronutrients(removedMeal.getMicronutrients());
        snapshot.setRationale(removedMeal.getRationale());
        slot.setOriginalMealSnapshot(snapshot);
        return slot;
    }

    private static void insertReplacementMeal(DietPlan plan, DietPlan.RemovedMealSlot slot, DietPlan.Meal replacement) {
        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            int dayIndex = slot.getDayIndex() == null ? 0 : slot.getDayIndex();
            if (dayIndex < 0 || dayIndex >= plan.getDays().size()) {
                throw new IllegalArgumentException("Invalid day index for replacement: " + dayIndex);
            }
            DietPlan.DayPlan day = plan.getDays().get(dayIndex);
            if (day.getMeals() == null) {
                day.setMeals(new ArrayList<>());
            }
            int insertionIndex = Math.max(0, Math.min(slot.getOriginalMealIndex(), day.getMeals().size()));
            day.getMeals().add(insertionIndex, replacement);
        } else {
            if (plan.getMeals() == null) {
                plan.setMeals(new ArrayList<>());
            }
            int insertionIndex = Math.max(0, Math.min(slot.getOriginalMealIndex(), plan.getMeals().size()));
            plan.getMeals().add(insertionIndex, replacement);
        }
    }

    private static void recomputeDayPlan(DietPlan.DayPlan day) {
        List<DietPlan.Meal> meals = day.getMeals() != null ? day.getMeals() : List.of();
        int kcal = meals.stream().mapToInt(DietPlan.Meal::getCalories).sum();
        double p = meals.stream().mapToDouble(DietPlan.Meal::getProteinGrams).sum();
        double c = meals.stream().mapToDouble(DietPlan.Meal::getCarbsGrams).sum();
        double f = meals.stream().mapToDouble(DietPlan.Meal::getFatGrams).sum();
        day.setDailyCalories(kcal);
        DietPlan.MacroBreakdown mb = new DietPlan.MacroBreakdown();
        if (kcal > 0) {
            mb.setProteinPercent((p * 4.0 / kcal) * 100.0);
            mb.setCarbsPercent((c * 4.0 / kcal) * 100.0);
            mb.setFatPercent((f * 9.0 / kcal) * 100.0);
        } else {
            mb.setProteinPercent(0);
            mb.setCarbsPercent(0);
            mb.setFatPercent(0);
        }
        day.setMacroBreakdown(mb);
    }

    private static void recomputeMultiDayPlanAggregates(DietPlan plan) {
        List<DietPlan.DayPlan> days = plan.getDays();
        if (days == null || days.isEmpty()) {
            return;
        }
        int sumKcal = days.stream().mapToInt(DietPlan.DayPlan::getDailyCalories).sum();
        plan.setTotalDays(days.size());
        plan.setDailyCalories(sumKcal / days.size());

        double pW = 0;
        double cW = 0;
        double fW = 0;
        double wSum = 0;
        for (DietPlan.DayPlan d : days) {
            int k = d.getDailyCalories();
            if (d.getMacroBreakdown() != null && k > 0) {
                pW += d.getMacroBreakdown().getProteinPercent() * k;
                cW += d.getMacroBreakdown().getCarbsPercent() * k;
                fW += d.getMacroBreakdown().getFatPercent() * k;
                wSum += k;
            }
        }
        DietPlan.MacroBreakdown planMb = new DietPlan.MacroBreakdown();
        if (wSum > 0) {
            planMb.setProteinPercent(pW / wSum);
            planMb.setCarbsPercent(cW / wSum);
            planMb.setFatPercent(fW / wSum);
        } else {
            planMb.setProteinPercent(0);
            planMb.setCarbsPercent(0);
            planMb.setFatPercent(0);
        }
        plan.setMacroBreakdown(planMb);
    }

    private static void recomputeLegacyPlanFromMeals(DietPlan plan) {
        List<DietPlan.Meal> meals = plan.getMeals() != null ? plan.getMeals() : List.of();
        int kcal = meals.stream().mapToInt(DietPlan.Meal::getCalories).sum();
        double p = meals.stream().mapToDouble(DietPlan.Meal::getProteinGrams).sum();
        double c = meals.stream().mapToDouble(DietPlan.Meal::getCarbsGrams).sum();
        double f = meals.stream().mapToDouble(DietPlan.Meal::getFatGrams).sum();
        plan.setDailyCalories(kcal);
        DietPlan.MacroBreakdown mb = new DietPlan.MacroBreakdown();
        if (kcal > 0) {
            mb.setProteinPercent((p * 4.0 / kcal) * 100.0);
            mb.setCarbsPercent((c * 4.0 / kcal) * 100.0);
            mb.setFatPercent((f * 9.0 / kcal) * 100.0);
        } else {
            mb.setProteinPercent(0);
            mb.setCarbsPercent(0);
            mb.setFatPercent(0);
        }
        plan.setMacroBreakdown(mb);
    }

    private CulturalValidationResult validateCulturalCoherence(DietPlan plan, List<String> selectedCuisines,
                                                               List<CulturalFoodGroup> culturalFoods) {
        if (selectedCuisines == null || selectedCuisines.size() < 2 || culturalFoods == null || culturalFoods.isEmpty()) {
            return CulturalValidationResult.success();
        }
        Map<String, Set<String>> cuisineFoodLexicon = buildCuisineFoodLexicon(selectedCuisines, culturalFoods);
        if (cuisineFoodLexicon.isEmpty()) {
            return CulturalValidationResult.success();
        }

        List<String> violations = new ArrayList<>();
        List<DietPlan.DayPlan> days = plan.getDays() != null && !plan.getDays().isEmpty()
                ? plan.getDays()
                : buildFallbackSingleDay(plan);

        for (int dayIdx = 0; dayIdx < days.size(); dayIdx++) {
            DietPlan.DayPlan day = days.get(dayIdx);
            Set<String> cuisinesUsedInDay = new LinkedHashSet<>();
            List<DietPlan.Meal> meals = day.getMeals() != null ? day.getMeals() : List.of();
            for (int mealIdx = 0; mealIdx < meals.size(); mealIdx++) {
                DietPlan.Meal meal = meals.get(mealIdx);
                Set<String> matchingCuisines = matchingCuisinesForMeal(meal, cuisineFoodLexicon);
                String mealName = meal.getName() != null ? meal.getName() : "Meal#" + (mealIdx + 1);
                if (matchingCuisines.isEmpty()) {
                    violations.add("Day " + (dayIdx + 1) + " " + mealName + ": off-cuisine (no selected cuisine match)");
                } else if (matchingCuisines.size() > 1) {
                    violations.add("Day " + (dayIdx + 1) + " " + mealName + ": mixed-cuisine meal " + matchingCuisines);
                } else {
                    cuisinesUsedInDay.add(matchingCuisines.iterator().next());
                }
            }
            if (cuisinesUsedInDay.size() < 2) {
                violations.add("Day " + (dayIdx + 1) + ": insufficient selected-cuisine diversity across meals");
            }
        }
        return violations.isEmpty() ? CulturalValidationResult.success() : CulturalValidationResult.failure(violations);
    }

    private List<DietPlan.DayPlan> buildFallbackSingleDay(DietPlan plan) {
        DietPlan.DayPlan fallback = new DietPlan.DayPlan();
        fallback.setDayNumber(1);
        fallback.setMeals(plan.getMeals() != null ? plan.getMeals() : List.of());
        return List.of(fallback);
    }

    private Map<String, Set<String>> buildCuisineFoodLexicon(List<String> selectedCuisines,
                                                              List<CulturalFoodGroup> culturalFoods) {
        Map<String, Set<String>> out = new HashMap<>();
        Set<String> selected = selectedCuisines.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(this::normalizeText)
                .collect(java.util.stream.Collectors.toSet());
        for (CulturalFoodGroup group : culturalFoods) {
            if (group.getCulture() == null || group.getFoods() == null) continue;
            String culture = normalizeText(group.getCulture());
            if (!selected.contains(culture)) continue;
            Set<String> foods = out.computeIfAbsent(culture, k -> new LinkedHashSet<>());
            for (CulturalFoodGroup.FoodEquivalent fe : group.getFoods()) {
                if (fe.getName() == null || fe.getName().isBlank()) continue;
                foods.add(normalizeText(fe.getName()));
            }
        }
        return out;
    }

    private Set<String> matchingCuisinesForMeal(DietPlan.Meal meal, Map<String, Set<String>> cuisineFoodLexicon) {
        Set<String> matched = new LinkedHashSet<>();
        List<String> mealFoodNames = new ArrayList<>();
        if (meal.getFoods() != null) {
            for (DietPlan.MealFood food : meal.getFoods()) {
                if (food.getName() != null && !food.getName().isBlank()) {
                    mealFoodNames.add(normalizeText(food.getName()));
                }
            }
        }
        if (mealFoodNames.isEmpty() && meal.getName() != null && !meal.getName().isBlank()) {
            mealFoodNames.add(normalizeText(meal.getName()));
        }
        for (Map.Entry<String, Set<String>> entry : cuisineFoodLexicon.entrySet()) {
            String cuisine = entry.getKey();
            Set<String> cuisineFoods = entry.getValue();
            boolean hasMatch = mealFoodNames.stream().anyMatch(mealFood ->
                    cuisineFoods.stream().anyMatch(cultureFood -> culturallyMatches(mealFood, cultureFood)));
            if (hasMatch) {
                matched.add(cuisine);
            }
        }
        return matched;
    }

    private String buildCulturalRetryConstraints(List<String> selectedCuisines, List<String> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("RETRY DUE TO CULTURAL VALIDATION FAILURE.\n");
        sb.append("Selected cuisines: ").append(String.join(", ", selectedCuisines)).append(".\n");
        sb.append("Hard rules:\n");
        sb.append("1) Each meal/snack must map to exactly ONE selected cuisine.\n");
        sb.append("2) Do not mix ingredients from multiple selected cuisines in one meal.\n");
        sb.append("3) If 2+ cuisines are selected, each day must include at least 2 selected cuisines across meals.\n");
        sb.append("4) Ensure each meal is authentic to its assigned cuisine using ingredients and methods from that cuisine.\n");
        if (violations != null && !violations.isEmpty()) {
            sb.append("Previous violations to avoid:\n");
            for (String violation : violations) {
                sb.append("- ").append(violation).append("\n");
            }
        }
        return sb.toString();
    }

    private boolean culturallyMatches(String mealFoodName, String cultureFoodName) {
        if (mealFoodName.equals(cultureFoodName)) return true;
        if (mealFoodName.length() < 4 || cultureFoodName.length() < 4) return false;
        return mealFoodName.contains(cultureFoodName) || cultureFoodName.contains(mealFoodName);
    }

    private String normalizeText(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record CulturalValidationResult(boolean valid, List<String> violations) {
        static CulturalValidationResult success() {
            return new CulturalValidationResult(true, List.of());
        }

        static CulturalValidationResult failure(List<String> violations) {
            return new CulturalValidationResult(false, violations == null ? List.of() : violations);
        }
    }

    private void rememberRejectedMeal(DietPlan.Meal meal, String sourcePlanId) {
        if (meal == null) {
            return;
        }
        List<String> rejected = new ArrayList<>();
        if (meal.getName() != null && !meal.getName().isBlank()) {
            rejected.add(meal.getName().trim());
        }
        if (meal.getFoods() != null) {
            for (DietPlan.MealFood food : meal.getFoods()) {
                if (food != null && food.getName() != null && !food.getName().isBlank()) {
                    rejected.add(food.getName().trim());
                }
            }
        }
        rememberRejectedFoods(rejected, "Removed from recommended plan", sourcePlanId);
    }

    private void rememberRejectedFoods(List<String> rejectedFoods, String reason, String sourcePlanId) {
        if (rejectedFoods == null || rejectedFoods.isEmpty()) {
            return;
        }
        Set<String> uniqueFoods = rejectedFoods.stream()
                .filter(food -> food != null && !food.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String food : uniqueFoods) {
            foodPreferenceService.addDislike(
                    food,
                    com.dietbuilder.model.FoodPreference.PreferenceType.TEMPORARY,
                    reason,
                    sourcePlanId
            );
        }
    }
}
