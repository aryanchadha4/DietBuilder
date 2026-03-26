package com.dietbuilder.service;

import com.dietbuilder.model.ConsentRecord;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyService {

    private final ConsentRecordRepository consentRecordRepository;
    private final UserProfileRepository userProfileRepository;
    private final DietPlanRepository dietPlanRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final SafetyAlertRepository safetyAlertRepository;
    private final OutcomeRecordRepository outcomeRecordRepository;

    public ConsentRecord recordConsent(String profileId, Map<String, Boolean> consents, String ipHash) {
        ConsentRecord record = consentRecordRepository.findByProfileId(profileId).orElse(new ConsentRecord());
        record.setProfileId(profileId);
        record.setConsents(consents);
        record.setGrantedAt(Instant.now());
        record.setIpHash(ipHash);
        record.setRevokedAt(null);
        return consentRecordRepository.save(record);
    }

    public Optional<ConsentRecord> getConsent(String profileId) {
        return consentRecordRepository.findByProfileId(profileId);
    }

    public boolean hasConsent(String profileId, String consentType) {
        return consentRecordRepository.findByProfileId(profileId)
                .map(r -> r.getConsents() != null && Boolean.TRUE.equals(r.getConsents().get(consentType)))
                .orElse(false);
    }

    public Map<String, Object> exportUserData(String profileId) {
        Map<String, Object> data = new LinkedHashMap<>();
        userProfileRepository.findById(profileId).ifPresent(p -> data.put("profile", p));
        data.put("dietPlans", dietPlanRepository.findByProfileIdOrderByCreatedAtDesc(profileId));
        data.put("outcomeRecords", outcomeRecordRepository.findByProfileIdOrderByRecordedAtDesc(profileId));
        consentRecordRepository.findByProfileId(profileId).ifPresent(c -> data.put("consent", c));
        data.put("exportedAt", Instant.now().toString());
        return data;
    }

    public boolean deleteUserData(String profileId) {
        try {
            dietPlanRepository.findByProfileIdOrderByCreatedAtDesc(profileId).forEach(dietPlanRepository::delete);
            safetyAlertRepository.findByProfileId(profileId).forEach(safetyAlertRepository::delete);
            outcomeRecordRepository.findByProfileIdOrderByRecordedAtDesc(profileId).forEach(outcomeRecordRepository::delete);
            consentRecordRepository.findByProfileId(profileId).ifPresent(consentRecordRepository::delete);
            userProfileRepository.deleteById(profileId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete user data for {}", profileId, e);
            return false;
        }
    }

    public boolean anonymizeProfile(String profileId) {
        Optional<UserProfile> opt = userProfileRepository.findById(profileId);
        if (opt.isEmpty()) return false;
        UserProfile profile = opt.get();
        profile.setName("Anonymous-" + profileId.substring(0, 6));
        profile.setMedicalInfo("");
        userProfileRepository.save(profile);
        return true;
    }
}
