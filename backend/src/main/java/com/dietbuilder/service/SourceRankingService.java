package com.dietbuilder.service;

import com.dietbuilder.model.ExpertSource;
import com.dietbuilder.model.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class SourceRankingService {
    @Value("${rag.ranking.credibility-weight:0.4}")
    private double credibilityWeight;

    @Value("${rag.ranking.recency-weight:0.2}")
    private double recencyWeight;

    @Value("${rag.ranking.relevance-weight:0.3}")
    private double relevanceWeight;

    @Value("${rag.ranking.specificity-weight:0.1}")
    private double specificityWeight;

    @Value("${rag.ranking.recency-half-life-years:5}")
    private double recencyHalfLifeYears;

    @Value("${rag.retrieval.min-relevance-threshold:0.3}")
    private double minCompositeThreshold;

    public List<RankedSource> rank(List<RankedSource> candidates, UserProfile profile) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<RankedSource> scored = new ArrayList<>();
        for (RankedSource candidate : candidates) {
            ExpertSource source = candidate.source();
            double relevance = Math.max(0.0, candidate.relevanceScore());
            double credibility = source.getCredibilityScore() > 0 ? source.getCredibilityScore() : defaultCredibility(source);
            double recency = computeRecencyScore(source.getPublicationDate());
            double specificity = computeSpecificity(source, profile);
            double composite = (credibility * credibilityWeight)
                    + (recency * recencyWeight)
                    + (relevance * relevanceWeight)
                    + (specificity * specificityWeight);
            if (composite >= minCompositeThreshold) {
                scored.add(new RankedSource(source, relevance, composite));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(RankedSource::compositeScore).reversed())
                .collect(Collectors.toList());
    }

    public double computeAverageRelevance(List<RankedSource> ranked) {
        if (ranked == null || ranked.isEmpty()) {
            return 0.0;
        }
        double sum = ranked.stream().mapToDouble(RankedSource::relevanceScore).sum();
        return sum / ranked.size();
    }

    private double defaultCredibility(ExpertSource source) {
        if (source == null || source.getSourceType() == null) {
            return 0.5;
        }
        return switch (source.getSourceType()) {
            case META_ANALYSIS, SYSTEMATIC_REVIEW -> 1.0;
            case PEER_REVIEWED, CLINICAL_GUIDELINE -> 0.9;
            case GOVERNMENT_GUIDELINE, POSITION_STATEMENT -> 0.85;
            case TEXTBOOK -> 0.7;
            case EXPERT_CONSENSUS -> 0.6;
        };
    }

    private double computeRecencyScore(String publicationDate) {
        if (publicationDate == null || publicationDate.isBlank()) {
            return 0.5;
        }
        int year = parseYear(publicationDate);
        if (year <= 0) {
            return 0.5;
        }
        int currentYear = LocalDate.now().getYear();
        double ageYears = Math.max(0, currentYear - year);
        double lambda = Math.log(2) / Math.max(1.0, recencyHalfLifeYears);
        double score = Math.exp(-lambda * ageYears);
        return Math.max(0.3, Math.min(1.0, score));
    }

    private int parseYear(String publicationDate) {
        try {
            if (publicationDate.length() == 4) {
                return Integer.parseInt(publicationDate);
            }
            return LocalDate.parse(publicationDate).getYear();
        } catch (NumberFormatException | DateTimeParseException ignored) {
            return 0;
        }
    }

    private double computeSpecificity(ExpertSource source, UserProfile profile) {
        if (source == null || profile == null) {
            return 0.5;
        }
        double score = 0.5;
        String gender = normalize(profile.getGender());
        int age = profile.getAge();
        List<String> goals = profile.getGoals() == null ? List.of() : profile.getGoals();
        String medical = normalize(profile.getMedicalInfo());

        if (!source.getGenderRelevance().isEmpty() && source.getGenderRelevance().stream().map(this::normalize).anyMatch(g -> g.equals(gender) || g.equals("all"))) {
            score += 0.15;
        }
        if (!source.getAgeGroups().isEmpty() && source.getAgeGroups().stream().map(this::normalize).anyMatch(g -> matchesAgeGroup(g, age))) {
            score += 0.15;
        }
        if (!source.getRelevantGoals().isEmpty()) {
            long matches = goals.stream().map(this::normalize)
                    .filter(g -> source.getRelevantGoals().stream().map(this::normalize).anyMatch(sg -> sg.contains(g) || g.contains(sg)))
                    .count();
            if (matches > 0) {
                score += 0.1;
            }
        }
        if (!source.getRelevantConditions().isEmpty() && !medical.isBlank()) {
            boolean conditionMatch = source.getRelevantConditions().stream()
                    .map(this::normalize)
                    .anyMatch(medical::contains);
            if (conditionMatch) {
                score += 0.1;
            }
        }
        return Math.min(1.0, score);
    }

    private boolean matchesAgeGroup(String ageGroup, int age) {
        return switch (ageGroup) {
            case "minor", "child", "adolescent" -> age < 18;
            case "young_adult" -> age >= 18 && age <= 30;
            case "adult" -> age >= 31 && age <= 64;
            case "senior", "older_adult" -> age >= 65;
            case "all" -> true;
            default -> false;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record RankedSource(ExpertSource source, double relevanceScore, double compositeScore) { }
}
