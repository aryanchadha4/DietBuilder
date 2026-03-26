package com.dietbuilder.controller;

import com.dietbuilder.model.FairnessReport;
import com.dietbuilder.service.BenchmarkService;
import com.dietbuilder.service.FairnessAuditService;
import com.dietbuilder.service.OutcomeComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class FairnessController {
    private final FairnessAuditService fairnessAuditService;
    private final BenchmarkService benchmarkService;
    private final OutcomeComparisonService outcomeComparisonService;

    @PostMapping("/fairness/run")
    public FairnessReport runFairnessAudit() {
        return fairnessAuditService.generateReport();
    }

    @GetMapping("/fairness/reports")
    public List<FairnessReport> getReports() {
        return fairnessAuditService.getReports();
    }

    @GetMapping("/fairness/reports/{id}")
    public FairnessReport getReport(@PathVariable String id) {
        return fairnessAuditService.getReport(id).orElseThrow(() -> new RuntimeException("Report not found"));
    }

    @PostMapping("/benchmarks/run")
    public BenchmarkService.BenchmarkResult runBenchmarks() {
        return benchmarkService.runSafetyBenchmarks();
    }

    @GetMapping("/outcomes/comparison/{profileId}")
    public OutcomeComparisonService.PredictionComparison getComparison(@PathVariable String profileId) {
        return outcomeComparisonService.comparePredictedVsActual(profileId);
    }
}
