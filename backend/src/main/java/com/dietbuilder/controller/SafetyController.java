package com.dietbuilder.controller;

import com.dietbuilder.model.SafetyAlert;
import com.dietbuilder.service.SafetyGuardrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/safety")
@RequiredArgsConstructor
public class SafetyController {
    private final SafetyGuardrailService safetyGuardrailService;

    @GetMapping("/alerts/{profileId}")
    public List<SafetyAlert> getAlertsForProfile(@PathVariable String profileId) {
        return safetyGuardrailService.getAlertsForProfile(profileId);
    }

    @GetMapping("/alerts/plan/{planId}")
    public List<SafetyAlert> getAlertsForPlan(@PathVariable String planId) {
        return safetyGuardrailService.getAlertsForPlan(planId);
    }
}
