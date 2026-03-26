package com.dietbuilder.service;

import com.dietbuilder.model.FoodItem;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FoodNutrientHelperTest {

    @Test
    void scalesNutrientsLinearlyWithGrams() {
        FoodItem chicken = new FoodItem();
        chicken.setFdcId(171705);
        chicken.setDescription("Chicken breast, cooked");
        FoodItem.NutrientValue energy = new FoodItem.NutrientValue(165, "kcal");
        FoodItem.NutrientValue protein = new FoodItem.NutrientValue(31, "g");
        chicken.setNutrients(Map.of(
                "energy", energy,
                "protein", protein
        ));

        Map<String, Double> n100 = FoodNutrientHelper.scaledKeyNutrients(chicken, 100);
        assertThat(n100.get("energy")).isEqualTo(165.0);
        assertThat(n100.get("protein")).isEqualTo(31.0);

        Map<String, Double> n50 = FoodNutrientHelper.scaledKeyNutrients(chicken, 50);
        assertThat(n50.get("energy")).isEqualTo(82.5);
        assertThat(n50.get("protein")).isEqualTo(15.5);
    }
}
