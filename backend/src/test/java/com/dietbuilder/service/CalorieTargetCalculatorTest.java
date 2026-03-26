package com.dietbuilder.service;

import com.dietbuilder.model.UserProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalorieTargetCalculatorTest {

    @Test
    void computesPositiveDailyTargetForTypicalMale() {
        CalorieTargetCalculator calc = new CalorieTargetCalculator();
        UserProfile p = new UserProfile();
        p.setAge(30);
        p.setGender("male");
        p.setHeightCm(180);
        p.setWeightKg(80);
        p.setGoals(java.util.List.of());

        CalorieTargetCalculator.Result r = calc.compute(p);
        assertThat(r.bmr()).isGreaterThan(1200);
        assertThat(r.dailyCalorieTarget()).isGreaterThan(1400);
        assertThat(r.tdee()).isGreaterThanOrEqualTo(r.bmr());
    }

    @Test
    void appliesDeficitWhenWeightLossGoal() {
        CalorieTargetCalculator calc = new CalorieTargetCalculator();
        UserProfile p = new UserProfile();
        p.setAge(40);
        p.setGender("female");
        p.setHeightCm(165);
        p.setWeightKg(65);
        p.setGoals(java.util.List.of("Lose weight"));

        CalorieTargetCalculator.Result r = calc.compute(p);
        assertThat(r.dailyCalorieTarget()).isLessThan(r.tdee());
    }
}
