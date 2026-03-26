package com.dietbuilder.service;

import com.dietbuilder.model.FoodItem;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scales per-100g nutrient values from {@link FoodItem} to a portion and maps keys to
 * {@link NutrientReferenceService} / audit field names.
 */
public final class FoodNutrientHelper {

    private static final Set<String> REFERENCE_KEYS = Set.of(
            "energy", "protein", "fiber",
            "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_e", "vitamin_k",
            "vitamin_b6", "vitamin_b12", "folate", "thiamin", "riboflavin", "niacin",
            "calcium", "iron", "magnesium", "zinc", "potassium", "sodium",
            "phosphorus", "selenium", "copper", "manganese", "chromium"
    );

    private FoodNutrientHelper() {}

    /**
     * Nutrients for a portion (grams), including macros and micronutrients for audit.
     * Keys align with nutrient-references.json where applicable; also includes carbohydrate and fat for meal totals.
     */
    public static Map<String, Double> scaledKeyNutrients(FoodItem food, double grams) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (food.getNutrients() == null || grams <= 0) {
            return out;
        }
        double factor = grams / 100.0;
        for (var e : food.getNutrients().entrySet()) {
            String raw = e.getKey();
            FoodItem.NutrientValue nv = e.getValue();
            if (nv == null) continue;
            double amt = nv.getAmount() * factor;
            String refKey = mapFoodKeyToReference(raw);
            if (refKey != null && REFERENCE_KEYS.contains(refKey)) {
                out.merge(refKey, amt, Double::sum);
            }
            String rk = raw.toLowerCase(Locale.ROOT);
            if ("carbohydrate".equals(rk) || "fat".equals(rk)) {
                out.merge(rk, amt, Double::sum);
            }
        }
        return out;
    }

    private static String mapFoodKeyToReference(String raw) {
        if (raw == null) return null;
        String k = raw.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "vitamin_c" -> "vitamin_c";
            case "vitamin_d" -> "vitamin_d";
            case "vitamin_e" -> "vitamin_e";
            case "vitamin_k" -> "vitamin_k";
            case "vitamin_b6" -> "vitamin_b6";
            case "vitamin_b12" -> "vitamin_b12";
            case "vitamin_a" -> "vitamin_a";
            case "energy", "protein", "fiber", "calcium", "iron", "magnesium", "zinc",
                 "potassium", "sodium", "phosphorus", "selenium", "copper", "manganese", "chromium",
                 "folate", "thiamin", "riboflavin", "niacin" -> k;
            default -> null;
        };
    }

    public static double energyPer100g(FoodItem food) {
        if (food.getNutrients() == null) return 0;
        FoodItem.NutrientValue ev = food.getNutrients().get("energy");
        return ev != null ? ev.getAmount() : 0;
    }

    public static double gramsForEnergyTarget(FoodItem food, double targetKcal) {
        double per100 = energyPer100g(food);
        if (per100 <= 1) return 100;
        double g = targetKcal * 100.0 / per100;
        return clamp(g, 25.0, 500.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
