package com.dietbuilder.service;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.SavedMeal;
import com.dietbuilder.model.User;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.DietPlanRepository;
import com.dietbuilder.repository.SavedMealRepository;
import com.dietbuilder.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedMealService {
    private final SavedMealRepository savedMealRepository;
    private final DietPlanRepository dietPlanRepository;
    private final UserProfileRepository profileRepository;
    private final AuthService authService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return authService.findByUsername(auth.getName());
    }

    public SavedMeal saveFromPlanMeal(String planId, Integer dayIndex, int mealIndex) {
        User user = getCurrentUser();
        DietPlan plan = loadOwnedPlan(planId, user.getId());
        DietPlan.Meal meal = extractMeal(plan, dayIndex, mealIndex);

        SavedMeal savedMeal = new SavedMeal();
        savedMeal.setUserId(user.getId());
        savedMeal.setProfileId(plan.getProfileId());
        savedMeal.setSourcePlanId(planId);
        savedMeal.setSourceDayIndex(dayIndex);
        savedMeal.setSourceMealIndex(mealIndex);
        savedMeal.setMeal(copyMeal(meal));
        return savedMealRepository.save(savedMeal);
    }

    public List<SavedMeal> listSavedMeals(String profileId) {
        User user = getCurrentUser();
        if (profileId == null || profileId.isBlank()) {
            return savedMealRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        }
        UserProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));
        if (!user.getId().equals(profile.getUserId())) {
            throw new RuntimeException("Access denied");
        }
        return savedMealRepository.findByUserIdAndProfileIdOrderByCreatedAtDesc(user.getId(), profileId);
    }

    public void deleteSavedMeal(String savedMealId) {
        User user = getCurrentUser();
        SavedMeal savedMeal = savedMealRepository.findById(savedMealId)
                .orElseThrow(() -> new RuntimeException("Saved meal not found: " + savedMealId));
        if (!user.getId().equals(savedMeal.getUserId())) {
            throw new RuntimeException("Access denied");
        }
        savedMealRepository.delete(savedMeal);
    }

    public DietPlan insertSavedMeal(String savedMealId, String targetPlanId, Integer dayIndex, int mealIndex) {
        User user = getCurrentUser();
        SavedMeal savedMeal = savedMealRepository.findById(savedMealId)
                .orElseThrow(() -> new RuntimeException("Saved meal not found: " + savedMealId));
        if (!user.getId().equals(savedMeal.getUserId())) {
            throw new RuntimeException("Access denied");
        }

        DietPlan plan = loadOwnedPlan(targetPlanId, user.getId());
        DietPlan.Meal replacement = copyMeal(savedMeal.getMeal());
        if (replacement == null) {
            throw new IllegalArgumentException("Saved meal has no meal content");
        }

        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            if (dayIndex == null) {
                throw new IllegalArgumentException("dayIndex is required for multi-day plans");
            }
            if (dayIndex < 0 || dayIndex >= plan.getDays().size()) {
                throw new IllegalArgumentException("Invalid dayIndex: " + dayIndex);
            }
            DietPlan.DayPlan day = plan.getDays().get(dayIndex);
            if (day.getMeals() == null || day.getMeals().isEmpty()) {
                throw new IllegalArgumentException("Target day has no meals to replace");
            }
            if (mealIndex < 0 || mealIndex >= day.getMeals().size()) {
                throw new IllegalArgumentException("Invalid mealIndex: " + mealIndex);
            }
            day.getMeals().set(mealIndex, replacement);
            recomputeDayPlan(day);
            recomputeMultiDayPlanAggregates(plan);
        } else {
            if (plan.getMeals() == null || plan.getMeals().isEmpty()) {
                throw new IllegalArgumentException("Target plan has no meals to replace");
            }
            if (mealIndex < 0 || mealIndex >= plan.getMeals().size()) {
                throw new IllegalArgumentException("Invalid mealIndex: " + mealIndex);
            }
            plan.getMeals().set(mealIndex, replacement);
            recomputeLegacyPlanFromMeals(plan);
        }

        plan.setNutrientAudit(null);
        return dietPlanRepository.save(plan);
    }

    private DietPlan loadOwnedPlan(String planId, String userId) {
        DietPlan plan = dietPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        UserProfile profile = profileRepository.findById(plan.getProfileId())
                .orElseThrow(() -> new RuntimeException("Profile not found: " + plan.getProfileId()));
        if (!userId.equals(profile.getUserId())) {
            throw new RuntimeException("Access denied");
        }
        return plan;
    }

    private static DietPlan.Meal extractMeal(DietPlan plan, Integer dayIndex, int mealIndex) {
        if (plan.getDays() != null && !plan.getDays().isEmpty()) {
            int idx = dayIndex != null ? dayIndex : 0;
            if (idx < 0 || idx >= plan.getDays().size()) {
                throw new IllegalArgumentException("Invalid dayIndex: " + dayIndex);
            }
            List<DietPlan.Meal> meals = plan.getDays().get(idx).getMeals();
            if (meals == null || mealIndex < 0 || mealIndex >= meals.size()) {
                throw new IllegalArgumentException("Invalid mealIndex: " + mealIndex);
            }
            return meals.get(mealIndex);
        }
        if (plan.getMeals() == null || mealIndex < 0 || mealIndex >= plan.getMeals().size()) {
            throw new IllegalArgumentException("Invalid mealIndex: " + mealIndex);
        }
        return plan.getMeals().get(mealIndex);
    }

    private static DietPlan.Meal copyMeal(DietPlan.Meal meal) {
        if (meal == null) {
            return null;
        }
        DietPlan.Meal copy = new DietPlan.Meal();
        copy.setName(meal.getName());
        copy.setCalories(meal.getCalories());
        copy.setProteinGrams(meal.getProteinGrams());
        copy.setCarbsGrams(meal.getCarbsGrams());
        copy.setFatGrams(meal.getFatGrams());
        copy.setFiberGrams(meal.getFiberGrams());
        copy.setMicronutrients(meal.getMicronutrients());
        copy.setRationale(meal.getRationale());
        copy.setFoods(meal.getFoods() == null ? new ArrayList<>() : new ArrayList<>(meal.getFoods()));
        return copy;
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
            int kcal = d.getDailyCalories();
            if (d.getMacroBreakdown() != null && kcal > 0) {
                pW += d.getMacroBreakdown().getProteinPercent() * kcal;
                cW += d.getMacroBreakdown().getCarbsPercent() * kcal;
                fW += d.getMacroBreakdown().getFatPercent() * kcal;
                wSum += kcal;
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
}
