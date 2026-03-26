package com.dietbuilder.service;

import com.dietbuilder.model.RecommendationLog;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.RecommendationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationAuditService {

    private final RecommendationLogRepository logRepository;

    public RecommendationLog logRecommendation(String profileId, String planId,
                                                UserProfile profile, List<String> cuisinePreferences,
                                                String modelName,
                                                String systemPrompt, String userMessage,
                                                List<String> safetyChecksRun,
                                                List<String> postProcessingSteps, long latencyMs,
                                                List<String> retrievedSourceIds,
                                                int totalSourcesCited,
                                                double avgSourceRelevance,
                                                String retrievalQuery) {
        RecommendationLog logEntry = new RecommendationLog();
        logEntry.setProfileId(profileId);
        logEntry.setPlanId(planId);
        logEntry.setTimestamp(Instant.now());
        logEntry.setModelName(modelName);
        logEntry.setModelVersion("latest");
        logEntry.setSystemPrompt(systemPrompt);
        logEntry.setUserMessage(userMessage != null ? userMessage.replaceAll("(?i)name:\\s*\\S+", "Name: [REDACTED]") : null);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("age", profile.getAge());
        snapshot.put("gender", profile.getGender());
        snapshot.put("heightCm", profile.getHeightCm());
        snapshot.put("weightKg", profile.getWeightKg());
        snapshot.put("goals", profile.getGoals());
        snapshot.put("cuisinePreferences", cuisinePreferences != null ? cuisinePreferences : List.of());
        logEntry.setProfileSnapshot(snapshot);
        logEntry.setSafetyChecksRun(safetyChecksRun);
        logEntry.setPostProcessingSteps(postProcessingSteps);
        logEntry.setNutrientDatabaseVersion("curated-v1");
        logEntry.setLatencyMs(latencyMs);
        logEntry.setRetrievedSourceIds(retrievedSourceIds);
        logEntry.setTotalSourcesRetrieved(retrievedSourceIds == null ? 0 : retrievedSourceIds.size());
        logEntry.setTotalSourcesCited(totalSourcesCited);
        logEntry.setAvgSourceRelevance(avgSourceRelevance);
        logEntry.setRetrievalQuery(retrievalQuery);
        return logRepository.save(logEntry);
    }

    public Optional<RecommendationLog> getLogForPlan(String planId) {
        return logRepository.findByPlanId(planId);
    }

    public List<RecommendationLog> getLogsForProfile(String profileId) {
        return logRepository.findByProfileId(profileId);
    }
}
