package com.dietbuilder.service;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.OutcomeRecord;
import com.dietbuilder.repository.DietPlanRepository;
import com.dietbuilder.repository.OutcomeRecordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutcomeComparisonService {
    private final DietPlanRepository dietPlanRepository;
    private final OutcomeRecordRepository outcomeRecordRepository;

    public PredictionComparison comparePredictedVsActual(String profileId) {
        List<DietPlan> plans = dietPlanRepository.findByProfileIdOrderByCreatedAtDesc(profileId);
        List<OutcomeRecord> outcomes = outcomeRecordRepository.findByProfileIdOrderByRecordedAtDesc(profileId);
        PredictionComparison comparison = new PredictionComparison();
        comparison.setProfileId(profileId);
        comparison.setTotalPlans(plans.size());
        comparison.setTotalOutcomes(outcomes.size());
        if (!outcomes.isEmpty()) {
            List<OutcomeRecord> withWeight = outcomes.stream().filter(o -> o.getWeightKg() != null).collect(Collectors.toList());
            if (withWeight.size() >= 2)
                comparison.setActualWeightChangeKg(withWeight.get(0).getWeightKg() - withWeight.get(withWeight.size() - 1).getWeightKg());
            comparison.setAvgAdherence(outcomes.stream().filter(o -> o.getAdherencePercent() != null).mapToDouble(OutcomeRecord::getAdherencePercent).average().orElse(0));
        }
        if (!plans.isEmpty()) {
            DietPlan latest = plans.get(0);
            if (latest.getNutrientAudit() != null)
                comparison.setPredictedAdequacyScore(latest.getNutrientAudit().getAdequacyScore());
            comparison.setPredictedDailyCalories(latest.getDailyCalories());
        }
        return comparison;
    }

    @Data
    public static class PredictionComparison {
        private String profileId;
        private int totalPlans;
        private int totalOutcomes;
        private double actualWeightChangeKg;
        private double avgAdherence;
        private double predictedAdequacyScore;
        private int predictedDailyCalories;
    }
}
