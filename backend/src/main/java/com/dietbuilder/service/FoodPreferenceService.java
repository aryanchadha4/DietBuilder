package com.dietbuilder.service;

import com.dietbuilder.model.FoodPreference;
import com.dietbuilder.model.User;
import com.dietbuilder.repository.FoodPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodPreferenceService {

    private final FoodPreferenceRepository foodPreferenceRepository;
    private final AuthService authService;

    private static final int DEFAULT_TEMPORARY_DAYS = 30;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return authService.findByUsername(auth.getName());
    }

    public FoodPreference addDislike(String foodName, FoodPreference.PreferenceType type,
                                     String reason, String sourcePlanId) {
        User user = getCurrentUser();
        Optional<FoodPreference> existing = foodPreferenceRepository
                .findByUserIdAndFoodNameIgnoreCase(user.getId(), foodName);

        FoodPreference pref;
        if (existing.isPresent()) {
            pref = existing.get();
            pref.setType(type);
            pref.setReason(reason);
            pref.setSourcePlanId(sourcePlanId);
        } else {
            pref = new FoodPreference();
            pref.setUserId(user.getId());
            pref.setFoodName(foodName);
            pref.setType(type);
            pref.setReason(reason);
            pref.setSourcePlanId(sourcePlanId);
        }

        if (type == FoodPreference.PreferenceType.TEMPORARY) {
            pref.setExpiresAt(Instant.now().plus(DEFAULT_TEMPORARY_DAYS, ChronoUnit.DAYS));
        } else {
            pref.setExpiresAt(null);
        }

        return foodPreferenceRepository.save(pref);
    }

    public void removeDislike(String id) {
        User user = getCurrentUser();
        FoodPreference pref = foodPreferenceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Preference not found: " + id));
        if (!user.getId().equals(pref.getUserId())) {
            throw new RuntimeException("Access denied");
        }
        foodPreferenceRepository.delete(pref);
    }

    public FoodPreference updatePreference(String id, FoodPreference.PreferenceType newType) {
        User user = getCurrentUser();
        FoodPreference pref = foodPreferenceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Preference not found: " + id));
        if (!user.getId().equals(pref.getUserId())) {
            throw new RuntimeException("Access denied");
        }
        pref.setType(newType);
        if (newType == FoodPreference.PreferenceType.PERMANENT) {
            pref.setExpiresAt(null);
        } else if (pref.getExpiresAt() == null) {
            pref.setExpiresAt(Instant.now().plus(DEFAULT_TEMPORARY_DAYS, ChronoUnit.DAYS));
        }
        return foodPreferenceRepository.save(pref);
    }

    public List<FoodPreference> getActivePreferences() {
        User user = getCurrentUser();
        return getActivePreferencesForUser(user.getId());
    }

    public List<FoodPreference> getActivePreferencesForUser(String userId) {
        Instant now = Instant.now();
        return foodPreferenceRepository.findByUserId(userId).stream()
                .filter(p -> p.getType() == FoodPreference.PreferenceType.PERMANENT
                        || p.getExpiresAt() == null
                        || p.getExpiresAt().isAfter(now))
                .collect(Collectors.toList());
    }

    public List<String> getActiveDislikedFoodNames(String userId) {
        return getActivePreferencesForUser(userId).stream()
                .map(FoodPreference::getFoodName)
                .collect(Collectors.toList());
    }

    /** Active dislikes for the authenticated user (same scope as plan generation). */
    public List<String> getActiveDislikedFoodNamesForCurrentUser() {
        return getActiveDislikedFoodNames(getCurrentUser().getId());
    }

    public void resetTemporaryDislikes() {
        User user = getCurrentUser();
        foodPreferenceRepository.deleteByUserIdAndType(user.getId(), FoodPreference.PreferenceType.TEMPORARY);
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpired() {
        List<FoodPreference> expired = foodPreferenceRepository
                .findByTypeAndExpiresAtBefore(FoodPreference.PreferenceType.TEMPORARY, Instant.now());
        if (!expired.isEmpty()) {
            log.info("Cleaning up {} expired temporary food preferences", expired.size());
            foodPreferenceRepository.deleteAll(expired);
        }
    }
}
