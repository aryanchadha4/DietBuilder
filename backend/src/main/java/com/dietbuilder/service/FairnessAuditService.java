package com.dietbuilder.service;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.FairnessReport;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.DietPlanRepository;
import com.dietbuilder.repository.FairnessReportRepository;
import com.dietbuilder.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FairnessAuditService {
    private final DietPlanRepository dietPlanRepository;
    private final UserProfileRepository userProfileRepository;
    private final FairnessReportRepository fairnessReportRepository;

    public FairnessReport generateReport() {
        List<DietPlan> allPlans = dietPlanRepository.findAll();
        List<UserProfile> allProfiles = userProfileRepository.findAll();
        Map<String, UserProfile> profileMap = new HashMap<>();
        for (UserProfile p : allProfiles) profileMap.put(p.getId(), p);

        Map<String, List<DietPlan>> byGender = new HashMap<>();
        Map<String, List<DietPlan>> byAgeGroup = new HashMap<>();
        Map<String, List<DietPlan>> byCulture = new HashMap<>();

        for (DietPlan plan : allPlans) {
            UserProfile profile = profileMap.get(plan.getProfileId());
            if (profile == null) continue;
            byGender.computeIfAbsent(profile.getGender() != null ? profile.getGender() : "unknown", k -> new ArrayList<>()).add(plan);
            String ageGroup = profile.getAge() < 18 ? "minor" : profile.getAge() < 30 ? "young_adult" : profile.getAge() < 50 ? "adult" : "senior";
            byAgeGroup.computeIfAbsent(ageGroup, k -> new ArrayList<>()).add(plan);
            String cultureKey = "none";
            if (plan.getCuisinePreferences() != null && !plan.getCuisinePreferences().isEmpty()) {
                cultureKey = plan.getCuisinePreferences().stream().sorted().collect(Collectors.joining("+"));
            }
            byCulture.computeIfAbsent(cultureKey, k -> new ArrayList<>()).add(plan);
        }

        FairnessReport report = new FairnessReport();
        report.setGeneratedAt(Instant.now());
        report.setTotalPlansAnalyzed(allPlans.size());
        report.setByGender(computeMetrics(byGender));
        report.setByAgeGroup(computeMetrics(byAgeGroup));
        report.setByCulture(computeMetrics(byCulture));

        List<String> disparities = new ArrayList<>();
        checkDisparities(report.getByGender(), "gender", disparities);
        checkDisparities(report.getByAgeGroup(), "ageGroup", disparities);
        checkDisparities(report.getByCulture(), "culture", disparities);
        report.setFlaggedDisparities(disparities);

        return fairnessReportRepository.save(report);
    }

    public List<FairnessReport> getReports() {
        return fairnessReportRepository.findAllByOrderByGeneratedAtDesc();
    }

    public Optional<FairnessReport> getReport(String id) {
        return fairnessReportRepository.findById(id);
    }

    private Map<String, FairnessReport.GroupMetrics> computeMetrics(Map<String, List<DietPlan>> grouped) {
        Map<String, FairnessReport.GroupMetrics> result = new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            List<DietPlan> plans = entry.getValue();
            FairnessReport.GroupMetrics m = new FairnessReport.GroupMetrics();
            m.setCount(plans.size());
            m.setAvgAdequacyScore(plans.stream().filter(p -> p.getNutrientAudit() != null).mapToDouble(p -> p.getNutrientAudit().getAdequacyScore()).average().orElse(0));
            m.setAvgCalorieAccuracy(plans.stream().filter(p -> p.getDailyCalories() > 0).mapToInt(DietPlan::getDailyCalories).average().orElse(0));
            long alertCount = plans.stream().filter(p -> p.getSafetyAlerts() != null && !p.getSafetyAlerts().isEmpty()).count();
            m.setSafetyAlertRate(plans.isEmpty() ? 0 : (double) alertCount / plans.size() * 100);
            result.put(entry.getKey(), m);
        }
        return result;
    }

    private void checkDisparities(Map<String, FairnessReport.GroupMetrics> metrics, String dim, List<String> disparities) {
        if (metrics == null || metrics.size() < 2) return;
        double max = metrics.values().stream().mapToDouble(FairnessReport.GroupMetrics::getAvgAdequacyScore).max().orElse(0);
        double min = metrics.values().stream().mapToDouble(FairnessReport.GroupMetrics::getAvgAdequacyScore).min().orElse(0);
        if (max - min > 15) disparities.add(String.format("%s: adequacy gap of %.1f%%", dim, max - min));
    }
}
