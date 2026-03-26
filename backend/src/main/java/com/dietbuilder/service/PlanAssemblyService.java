package com.dietbuilder.service;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.FoodItem;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic multi-day plan from {@link MealBankService.MealPools} and {@link CalorieTargetCalculator}.
 */
@Service
@RequiredArgsConstructor
public class PlanAssemblyService {

    private final CalorieTargetCalculator calorieTargetCalculator;
    private final FoodItemRepository foodItemRepository;

    private static final double[] SLOT_FRAC = {0.25, 0.35, 0.30, 0.10};
    private static final String[] SLOT_NAMES = {"Breakfast", "Lunch", "Dinner", "Snack"};

    public DietPlan assembleHybridPlan(UserProfile profile,
                                       MealBankService.MealPools pools,
                                       int numDays,
                                       int firstDayNumber,
                                       List<String> cuisines,
                                       long seed) {
        CalorieTargetCalculator.Result tgt = calorieTargetCalculator.compute(profile);
        double dailyKcal = tgt.dailyCalorieTarget();

        List<FoodItem> proteins = merge(pools.proteins(), pools.legumes());
        List<FoodItem> grains = pools.grains();
        List<FoodItem> veg = pools.vegetables();
        List<FoodItem> fruit = pools.fruits();
        List<FoodItem> dairy = pools.dairy();
        List<FoodItem> nuts = pools.nutsAndOils();
        List<FoodItem> fallback = pools.fallback();

        List<DietPlan.DayPlan> days = new ArrayList<>();
        for (int d = 0; d < numDays; d++) {
            int dayNum = firstDayNumber + d;
            List<DietPlan.Meal> meals = new ArrayList<>();

            meals.add(buildSlot(SLOT_NAMES[0], dailyKcal * SLOT_FRAC[0], SlotRecipe.BREAKFAST,
                    proteins, grains, fruit, veg, dairy, nuts, fallback, seed, dayNum, 0));
            meals.add(buildSlot(SLOT_NAMES[1], dailyKcal * SLOT_FRAC[1], SlotRecipe.LUNCH,
                    proteins, grains, fruit, veg, dairy, nuts, fallback, seed, dayNum, 1));
            meals.add(buildSlot(SLOT_NAMES[2], dailyKcal * SLOT_FRAC[2], SlotRecipe.DINNER,
                    proteins, grains, fruit, veg, dairy, nuts, fallback, seed, dayNum, 2));
            meals.add(buildSlot(SLOT_NAMES[3], dailyKcal * SLOT_FRAC[3], SlotRecipe.SNACK,
                    proteins, grains, fruit, veg, dairy, nuts, fallback, seed, dayNum, 3));

            DietPlan.DayPlan day = new DietPlan.DayPlan();
            day.setDayNumber(dayNum);
            day.setLabel("Day " + dayNum + dayLabelSuffix(cuisines));
            day.setMeals(meals);
            scaleDayToTarget(day, dailyKcal);
            days.add(day);
        }

        DietPlan plan = new DietPlan();
        plan.setDays(days);
        plan.setTotalDays(numDays);
        plan.setDailyCalories((int) Math.round(dailyKcal));

        if (!days.isEmpty()) {
            DietPlan.DayPlan first = days.get(0);
            plan.setMeals(first.getMeals());
            plan.setDailyCalories(first.getDailyCalories());
            plan.setMacroBreakdown(first.getMacroBreakdown());
        }

        plan.setPlanContent(buildStubPlanContent(profile, tgt));
        plan.setNotes("Assembled deterministically from USDA curated foods; optional AI polish may update titles.");
        recomputePlanAggregates(plan);
        return plan;
    }

    private enum SlotRecipe { BREAKFAST, LUNCH, DINNER, SNACK }

    private DietPlan.Meal buildSlot(String name, double mealKcal, SlotRecipe recipe,
                                    List<FoodItem> proteins, List<FoodItem> grains, List<FoodItem> fruit,
                                    List<FoodItem> veg, List<FoodItem> dairy, List<FoodItem> nuts,
                                    List<FoodItem> fallback, long seed, int dayNum, int slotIdx) {
        DietPlan.Meal meal = new DietPlan.Meal();
        meal.setName(name);
        List<DietPlan.MealFood> foods = new ArrayList<>();

        int salt = (int) (seed + dayNum * 131L + slotIdx * 17);

        switch (recipe) {
            case BREAKFAST -> {
                addPortion(foods, pick(grains, fallback, salt), mealKcal * 0.38, salt + 1);
                addPortion(foods, pick(proteins, fallback, salt + 2), mealKcal * 0.32, salt + 3);
                addPortion(foods, pick(fruit, fallback, salt + 4), mealKcal * 0.30, salt + 5);
            }
            case LUNCH, DINNER -> {
                addPortion(foods, pick(proteins, fallback, salt), mealKcal * 0.42, salt + 1);
                addPortion(foods, pick(grains, fallback, salt + 2), mealKcal * 0.33, salt + 3);
                addPortion(foods, pick(veg, fallback, salt + 4), mealKcal * 0.25, salt + 5);
            }
            case SNACK -> {
                FoodItem f1 = pick(fruit, fallback, salt);
                FoodItem f2 = pick(nuts, dairy, fallback, salt + 1);
                addPortion(foods, f1, mealKcal * 0.55, salt + 2);
                addPortion(foods, f2, mealKcal * 0.45, salt + 3);
            }
        }

        meal.setFoods(foods);
        recomputeMealTotals(meal);
        return meal;
    }

    private static void addPortion(List<DietPlan.MealFood> foods, FoodItem item, double targetKcal, int salt) {
        if (item == null) return;
        double g = FoodNutrientHelper.gramsForEnergyTarget(item, targetKcal);
        DietPlan.MealFood mf = new DietPlan.MealFood();
        mf.setFdcId(String.valueOf(item.getFdcId()));
        mf.setName(item.getDescription());
        mf.setQuantityGrams(g);
        mf.setKeyNutrients(FoodNutrientHelper.scaledKeyNutrients(item, g));
        foods.add(mf);
    }

    private static FoodItem pick(List<FoodItem> primary, List<FoodItem> fallback, int salt) {
        List<FoodItem> pool = primary != null && !primary.isEmpty() ? primary : fallback;
        if (pool.isEmpty()) return null;
        int idx = Math.floorMod(salt, pool.size());
        return pool.get(idx);
    }

    private static FoodItem pick(List<FoodItem> a, List<FoodItem> b, List<FoodItem> fallback, int salt) {
        FoodItem x = pick(a, fallback, salt);
        if (x != null) return x;
        return pick(b, fallback, salt + 1);
    }

    private static List<FoodItem> merge(List<FoodItem> a, List<FoodItem> b) {
        List<FoodItem> out = new ArrayList<>();
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out;
    }

    private void scaleDayToTarget(DietPlan.DayPlan day, double targetKcal) {
        List<DietPlan.Meal> meals = day.getMeals();
        if (meals == null || meals.isEmpty()) return;
        int sum = meals.stream().mapToInt(DietPlan.Meal::getCalories).sum();
        if (sum <= 0) return;
        double factor = targetKcal / sum;
        if (Math.abs(factor - 1.0) < 0.03) {
            recomputeDay(day);
            return;
        }
        for (DietPlan.Meal m : meals) {
            for (DietPlan.MealFood mf : m.getFoods()) {
                mf.setQuantityGrams(mf.getQuantityGrams() * factor);
                Optional<FoodItem> fi = parseFdc(mf.getFdcId())
                        .flatMap(foodItemRepository::findByFdcId);
                fi.ifPresent(f -> mf.setKeyNutrients(FoodNutrientHelper.scaledKeyNutrients(f, mf.getQuantityGrams())));
            }
            recomputeMealTotals(m);
        }
        recomputeDay(day);
    }

    private static Optional<Integer> parseFdc(String fdcId) {
        if (fdcId == null || fdcId.isBlank()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(fdcId.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void recomputeMealTotals(DietPlan.Meal meal) {
        int kcal = 0;
        double p = 0, c = 0, f = 0, fib = 0;
        for (DietPlan.MealFood mf : meal.getFoods()) {
            var kn = mf.getKeyNutrients();
            if (kn == null) continue;
            kcal += kn.getOrDefault("energy", 0.0);
            p += kn.getOrDefault("protein", 0.0);
            c += kn.getOrDefault("carbohydrate", 0.0);
            f += kn.getOrDefault("fat", 0.0);
            fib += kn.getOrDefault("fiber", 0.0);
        }
        meal.setCalories(kcal);
        meal.setProteinGrams(p);
        meal.setCarbsGrams(c);
        meal.setFatGrams(f);
        meal.setFiberGrams(fib);
    }

    private static void recomputeDay(DietPlan.DayPlan day) {
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

    private void recomputePlanAggregates(DietPlan plan) {
        List<DietPlan.DayPlan> days = plan.getDays();
        if (days == null || days.isEmpty()) return;
        int sumKcal = days.stream().mapToInt(DietPlan.DayPlan::getDailyCalories).sum();
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

    private static String dayLabelSuffix(List<String> cuisines) {
        if (cuisines == null || cuisines.isEmpty()) return "";
        if (cuisines.size() == 1) return " — " + cuisines.get(0);
        return " — Mixed cuisines";
    }

    private static String buildStubPlanContent(UserProfile profile, CalorieTargetCalculator.Result tgt) {
        return String.format(
                "## Targets (server-calculated)%n%n- BMR: %.0f kcal%n- TDEE: %.0f kcal (activity × %.2f; ~%.0f min/day exercise)%n- Daily calorie target: %.0f kcal (%s)%n%n"
                        + "Meals were assembled from the curated USDA food bank with portion scaling; micronutrient audit is computed server-side.",
                tgt.bmr(), tgt.tdee(), tgt.activityMultiplier(), tgt.avgDailyExerciseMinutes(),
                tgt.dailyCalorieTarget(), tgt.goalAdjustmentRationale());
    }
}
