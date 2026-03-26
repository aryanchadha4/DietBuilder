package com.dietbuilder.service;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.SafetyAlert;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.SafetyAlertRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyGuardrailService {

    private final NutrientReferenceService nutrientReferenceService;
    private final SafetyAlertRepository safetyAlertRepository;

    public SafetyCheckResult runPreChecks(UserProfile profile) {
        List<DietPlan.SafetyAlert> alerts = new ArrayList<>();
        boolean blocked = false;

        double bmi = profile.getWeightKg() / Math.pow(profile.getHeightCm() / 100.0, 2);
        String medicalInfo = profile.getMedicalInfo() != null ? profile.getMedicalInfo().toLowerCase() : "";
        String goals = profile.getGoals() != null ? String.join(" ", profile.getGoals()).toLowerCase() : "";

        if (bmi < 18.5 && (goals.contains("weight loss") || goals.contains("lose weight") || goals.contains("fat loss"))) {
            DietPlan.SafetyAlert alert = new DietPlan.SafetyAlert();
            alert.setCheckType("ED_RISK");
            alert.setSeverity("BLOCK");
            alert.setMessage("Underweight BMI (" + String.format("%.1f", bmi) + ") combined with weight-loss goal detected.");
            alert.setRecommendation("Please consult a healthcare provider before pursuing weight loss.");
            alerts.add(alert);
            blocked = true;
        }

        if (medicalInfo.contains("eating disorder") || medicalInfo.contains("anorexia") || medicalInfo.contains("bulimia")) {
            DietPlan.SafetyAlert alert = new DietPlan.SafetyAlert();
            alert.setCheckType("ED_RISK");
            alert.setSeverity("BLOCK");
            alert.setMessage("Eating disorder history detected in medical information.");
            alert.setRecommendation("Diet plan generation requires clearance from your treatment team.");
            alerts.add(alert);
            blocked = true;
        }

        if (profile.getAge() < 18) {
            DietPlan.SafetyAlert alert = new DietPlan.SafetyAlert();
            alert.setCheckType("MINOR_RESTRICTION");
            alert.setSeverity("WARNING");
            alert.setMessage("User is under 18. Caloric restriction must be carefully managed.");
            alert.setRecommendation("Ensure adequate calories and nutrients for growth and development.");
            alerts.add(alert);
        }

        SafetyCheckResult result = new SafetyCheckResult();
        result.setAlerts(alerts);
        result.setBlocked(blocked);
        return result;
    }

    public SafetyCheckResult runPostChecks(DietPlan plan, UserProfile profile) {
        List<DietPlan.SafetyAlert> alerts = new ArrayList<>();
        boolean blocked = false;

        if (plan.getDailyCalories() > 0 && plan.getDailyCalories() < 1200) {
            DietPlan.SafetyAlert alert = new DietPlan.SafetyAlert();
            alert.setCheckType("VLCD");
            alert.setMessage("Very Low Calorie Diet detected (" + plan.getDailyCalories() + " kcal).");
            alert.setRecommendation("Consider increasing calories or consulting a healthcare provider.");
            if (plan.getDailyCalories() < 800) {
                alert.setSeverity("BLOCK");
                blocked = true;
            } else {
                alert.setSeverity("WARNING");
            }
            alerts.add(alert);
        }

        if (plan.getMacroBreakdown() != null) {
            if (plan.getMacroBreakdown().getProteinPercent() > 45) {
                DietPlan.SafetyAlert a = new DietPlan.SafetyAlert();
                a.setCheckType("EXTREME_MACROS");
                a.setSeverity("WARNING");
                a.setMessage("Protein exceeds 45% of calories.");
                a.setRecommendation("Consider reducing protein unless medically directed.");
                alerts.add(a);
            }
        }

        if (plan.getNutrientAudit() != null && plan.getNutrientAudit().getNutrients() != null) {
            for (var entry : plan.getNutrientAudit().getNutrients().entrySet()) {
                DietPlan.NutrientStatus ns = entry.getValue();
                if ("EXCESSIVE".equals(ns.getStatus())) {
                    DietPlan.SafetyAlert a = new DietPlan.SafetyAlert();
                    a.setCheckType("UL_EXCEEDED");
                    a.setSeverity("WARNING");
                    a.setMessage(entry.getKey() + " exceeds UL (" + String.format("%.1f", ns.getPlanned()) + " vs " + String.format("%.1f", ns.getUl()) + " " + ns.getUnit() + ").");
                    a.setRecommendation("Review food sources of " + entry.getKey() + ".");
                    alerts.add(a);
                }
            }
        }

        SafetyCheckResult result = new SafetyCheckResult();
        result.setAlerts(alerts);
        result.setBlocked(blocked);
        return result;
    }

    public void persistAlerts(List<DietPlan.SafetyAlert> alerts, String profileId, String planId) {
        for (DietPlan.SafetyAlert a : alerts) {
            SafetyAlert entity = new SafetyAlert();
            entity.setProfileId(profileId);
            entity.setPlanId(planId);
            entity.setCheckType(a.getCheckType());
            entity.setSeverity(a.getSeverity());
            entity.setMessage(a.getMessage());
            entity.setRecommendation(a.getRecommendation());
            safetyAlertRepository.save(entity);
        }
    }

    public List<SafetyAlert> getAlertsForProfile(String profileId) {
        return safetyAlertRepository.findByProfileId(profileId);
    }

    public List<SafetyAlert> getAlertsForPlan(String planId) {
        return safetyAlertRepository.findByPlanId(planId);
    }

    @Data
    public static class SafetyCheckResult {
        private List<DietPlan.SafetyAlert> alerts = new ArrayList<>();
        private boolean blocked;
    }
}
