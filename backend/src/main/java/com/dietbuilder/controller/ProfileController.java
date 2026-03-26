package com.dietbuilder.controller;

import com.dietbuilder.model.UserProfile;
import com.dietbuilder.service.DietRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final DietRecommendationService recommendationService;

    @GetMapping
    public List<UserProfile> getAllProfiles() {
        return recommendationService.getAllProfiles();
    }

    @GetMapping("/{id}")
    public UserProfile getProfile(@PathVariable String id) {
        return recommendationService.getProfile(id);
    }

    @PostMapping
    public ResponseEntity<UserProfile> createProfile(@Valid @RequestBody UserProfile profile) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recommendationService.saveProfile(profile));
    }

    @PutMapping("/{id}")
    public UserProfile updateProfile(@PathVariable String id, @RequestBody UserProfile profile) {
        return recommendationService.updateProfile(id, profile);
    }
}
