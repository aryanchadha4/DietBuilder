package com.dietbuilder.service;

import com.dietbuilder.model.UserProfile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared BMR / TDEE / daily calorie target logic (Mifflin–St Jeor + activity + goal adjustment).
 * Used by {@link OpenAIService} prompts and hybrid {@link PlanAssemblyService}.
 */
@Component
public class CalorieTargetCalculator {

    public record Result(
            double bmr,
            double tdee,
            double activityMultiplier,
            double avgDailyExerciseMinutes,
            double dailyCalorieTarget,
            String goalAdjustmentRationale
    ) {}

    public Result compute(UserProfile profile) {
        double avgDailyMinutes = computeAvgDailyExerciseMinutes(profile);
        double bmr = computeBmr(profile);
        double activityMultiplier = activityMultiplierFor(avgDailyMinutes);
        double tdee = bmr * activityMultiplier;

        double calorieTarget = tdee;
        String rationale = "maintenance";
        List<String> goals = profile.getGoals() != null ? profile.getGoals() : List.of();
        boolean wantsLoss = goals.stream().anyMatch(g -> {
            String x = g.toLowerCase();
            return x.contains("lose weight") || x.contains("reduce body fat");
        });
        boolean wantsGain = goals.stream().anyMatch(g -> {
            String x = g.toLowerCase();
            return x.contains("build muscle") || x.contains("gain weight");
        });
        if (wantsLoss && !wantsGain) {
            calorieTarget = tdee - 500;
            rationale = "deficit of 500 kcal for fat loss (~1 lb/week)";
        } else if (wantsGain && !wantsLoss) {
            calorieTarget = tdee + 300;
            rationale = "surplus of 300 kcal for lean muscle gain";
        } else if (wantsGain && wantsLoss) {
            calorieTarget = tdee - 200;
            rationale = "mild deficit of 200 kcal for body recomposition";
        }

        return new Result(bmr, tdee, activityMultiplier, avgDailyMinutes, calorieTarget, rationale);
    }

    public static double computeAvgDailyExerciseMinutes(UserProfile profile) {
        double avgDailyMinutes = 0;
        if (profile.getStrengthTraining() != null) {
            avgDailyMinutes += profile.getStrengthTraining().stream()
                    .mapToDouble(e -> e.getDaysPerWeek() * e.getDurationMinutes() / 7.0)
                    .sum();
        }
        if (profile.getCardioSchedule() != null) {
            avgDailyMinutes += profile.getCardioSchedule().stream()
                    .mapToDouble(e -> e.getDaysPerWeek() * e.getDurationMinutes() / 7.0)
                    .sum();
        }
        return avgDailyMinutes;
    }

    public static double computeBmr(UserProfile profile) {
        String sex = profile.getGender() != null ? profile.getGender().toLowerCase() : "male";
        if (sex.startsWith("f")) {
            return 10 * profile.getWeightKg() + 6.25 * profile.getHeightCm() - 5 * profile.getAge() - 161;
        }
        return 10 * profile.getWeightKg() + 6.25 * profile.getHeightCm() - 5 * profile.getAge() + 5;
    }

    private static double activityMultiplierFor(double avgDailyMinutes) {
        if (avgDailyMinutes < 15) return 1.2;
        if (avgDailyMinutes < 30) return 1.375;
        if (avgDailyMinutes < 60) return 1.55;
        if (avgDailyMinutes < 90) return 1.725;
        return 1.9;
    }
}
