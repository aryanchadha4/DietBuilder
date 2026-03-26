package com.dietbuilder.controller;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.OutcomeRecord;
import com.dietbuilder.service.LongitudinalAdaptationService;
import com.dietbuilder.service.OutcomeTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutcomeController {
    private final OutcomeTrackingService outcomeTrackingService;
    private final LongitudinalAdaptationService adaptationService;

    @PostMapping("/outcomes")
    public ResponseEntity<OutcomeRecord> recordOutcome(@RequestBody OutcomeRecord record) {
        return ResponseEntity.status(HttpStatus.CREATED).body(outcomeTrackingService.recordOutcome(record));
    }

    @GetMapping("/outcomes/{profileId}")
    public List<OutcomeRecord> getOutcomeHistory(@PathVariable String profileId) {
        return outcomeTrackingService.getOutcomeHistory(profileId);
    }

    @GetMapping("/outcomes/trends/{profileId}")
    public OutcomeTrackingService.OutcomeTrends getTrends(@PathVariable String profileId) {
        return outcomeTrackingService.computeTrends(profileId);
    }

    @GetMapping("/outcomes/adapt/{profileId}")
    public LongitudinalAdaptationService.AdaptationAssessment checkAdaptation(@PathVariable String profileId) {
        return adaptationService.shouldAdaptPlan(profileId);
    }

    @PostMapping("/plans/{profileId}/adapt")
    public ResponseEntity<DietPlan> adaptPlan(@PathVariable String profileId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adaptationService.generateAdaptedPlan(profileId));
    }
}
