package com.dietbuilder.service;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {
    private final SafetyGuardrailService safetyGuardrailService;
    private final ObjectMapper objectMapper;
    private JsonNode benchmarkData;

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("data/benchmark-cases.json").getInputStream();
            benchmarkData = objectMapper.readTree(is);
        } catch (Exception e) {
            log.warn("Could not load benchmark-cases.json: {}", e.getMessage());
        }
    }

    public BenchmarkResult runSafetyBenchmarks() {
        BenchmarkResult result = new BenchmarkResult();
        result.setCaseResults(new ArrayList<>());
        if (benchmarkData == null) return result;
        int passed = 0, total = 0, expectedTotal = 0, actualHit = 0;
        for (JsonNode c : benchmarkData.path("cases")) {
            total++;
            BenchmarkCaseResult cr = new BenchmarkCaseResult();
            cr.setCaseName(c.path("name").asText("unnamed"));
            UserProfile profile = new UserProfile();
            JsonNode pn = c.path("profile");
            profile.setName(pn.path("name").asText("Benchmark"));
            profile.setAge(pn.path("age").asInt(30));
            profile.setGender(pn.path("gender").asText("female"));
            profile.setHeightCm(pn.path("heightCm").asDouble(165));
            profile.setWeightKg(pn.path("weightKg").asDouble(60));
            profile.setMedicalInfo(pn.path("medicalInfo").asText(""));
            List<String> goals = new ArrayList<>();
            for (JsonNode g : pn.path("goals")) goals.add(g.asText());
            profile.setGoals(goals);
            SafetyGuardrailService.SafetyCheckResult check = safetyGuardrailService.runPreChecks(profile);
            List<String> actual = check.getAlerts().stream().map(DietPlan.SafetyAlert::getCheckType).collect(Collectors.toList());
            List<String> expected = new ArrayList<>();
            for (JsonNode f : c.path("expected").path("safetyFlags")) expected.add(f.asText());
            cr.setExpectedFlags(expected);
            cr.setActualFlags(actual);
            cr.setPassed(actual.containsAll(expected));
            if (cr.isPassed()) passed++;
            expectedTotal += expected.size();
            actualHit += (int) expected.stream().filter(actual::contains).count();
            result.getCaseResults().add(cr);
        }
        result.setTotalCases(total);
        result.setPassedCases(passed);
        result.setPassRate(total == 0 ? 0 : (double) passed / total * 100);
        result.setSafetyFlagRecall(expectedTotal == 0 ? 100 : (double) actualHit / expectedTotal * 100);
        return result;
    }

    @Data
    public static class BenchmarkResult {
        private int totalCases;
        private int passedCases;
        private double passRate;
        private double safetyFlagRecall;
        private List<BenchmarkCaseResult> caseResults;
    }

    @Data
    public static class BenchmarkCaseResult {
        private String caseName;
        private List<String> expectedFlags;
        private List<String> actualFlags;
        private boolean passed;
    }
}
