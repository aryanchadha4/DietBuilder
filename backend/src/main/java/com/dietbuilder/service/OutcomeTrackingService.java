package com.dietbuilder.service;

import com.dietbuilder.model.OutcomeRecord;
import com.dietbuilder.repository.OutcomeRecordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutcomeTrackingService {

    private final OutcomeRecordRepository outcomeRecordRepository;

    public OutcomeRecord recordOutcome(OutcomeRecord record) {
        if (record.getProfileId() == null || record.getProfileId().isBlank())
            throw new IllegalArgumentException("profileId is required");
        if (record.getRecordedAt() == null) record.setRecordedAt(Instant.now());
        return outcomeRecordRepository.save(record);
    }

    public List<OutcomeRecord> getOutcomeHistory(String profileId) {
        return outcomeRecordRepository.findByProfileIdOrderByRecordedAtDesc(profileId);
    }

    public OutcomeTrends computeTrends(String profileId) {
        List<OutcomeRecord> records = outcomeRecordRepository.findByProfileIdOrderByRecordedAtDesc(profileId);
        OutcomeTrends trends = new OutcomeTrends();
        trends.setTotalRecords(records.size());
        if (records.size() < 2) {
            trends.setWeightChangeKg(0);
            trends.setAvgAdherence(records.isEmpty() ? 0 :
                    records.get(0).getAdherencePercent() != null ? records.get(0).getAdherencePercent() : 0);
            trends.setSymptomFrequency(Map.of());
            return trends;
        }
        List<OutcomeRecord> withWeight = records.stream()
                .filter(r -> r.getWeightKg() != null)
                .collect(Collectors.toList());
        if (withWeight.size() >= 2)
            trends.setWeightChangeKg(withWeight.get(0).getWeightKg() - withWeight.get(withWeight.size() - 1).getWeightKg());
        trends.setAvgAdherence(records.stream()
                .filter(r -> r.getAdherencePercent() != null)
                .mapToDouble(OutcomeRecord::getAdherencePercent)
                .average().orElse(0));
        Map<String, Integer> symptomCounts = new LinkedHashMap<>();
        for (OutcomeRecord r : records) {
            if (r.getSymptoms() != null)
                for (String s : r.getSymptoms()) symptomCounts.merge(s, 1, Integer::sum);
        }
        trends.setSymptomFrequency(symptomCounts);
        return trends;
    }

    @Data
    public static class OutcomeTrends {
        private int totalRecords;
        private double weightChangeKg;
        private double avgAdherence;
        private Map<String, Integer> symptomFrequency;
    }
}
