package com.dietbuilder.controller;

import com.dietbuilder.model.RecommendationLog;
import com.dietbuilder.service.RecommendationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {
    private final RecommendationAuditService auditService;

    @GetMapping("/plan/{planId}")
    public RecommendationLog getLogForPlan(@PathVariable String planId) {
        return auditService.getLogForPlan(planId).orElseThrow(() -> new RuntimeException("No audit log found for plan: " + planId));
    }

    @GetMapping("/profile/{profileId}")
    public List<RecommendationLog> getLogsForProfile(@PathVariable String profileId) {
        return auditService.getLogsForProfile(profileId);
    }
}
