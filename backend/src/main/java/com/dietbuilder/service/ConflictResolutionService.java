package com.dietbuilder.service;

import com.dietbuilder.model.DietPlan;
import com.dietbuilder.model.ExpertSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ConflictResolutionService {

    public List<DietPlan.ConflictNote> resolve(List<ExpertSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        Map<String, List<ExpertSource>> byTopic = new LinkedHashMap<>();
        for (ExpertSource source : sources) {
            if (source.getTopics() == null || source.getTopics().isEmpty()) {
                byTopic.computeIfAbsent("general nutrition", t -> new ArrayList<>()).add(source);
                continue;
            }
            for (String topic : source.getTopics()) {
                String normalized = normalize(topic);
                if (!normalized.isBlank()) {
                    byTopic.computeIfAbsent(normalized, t -> new ArrayList<>()).add(source);
                }
            }
        }

        List<DietPlan.ConflictNote> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<ExpertSource>> entry : byTopic.entrySet()) {
            List<ExpertSource> topicSources = entry.getValue();
            if (topicSources.size() < 2) {
                continue;
            }
            List<ExpertSource> supporting = new ArrayList<>();
            List<ExpertSource> opposing = new ArrayList<>();
            for (ExpertSource source : topicSources) {
                String text = normalize(source.getSummary() + " " + source.getAbstractText() + " " + String.join(" ", safe(source.getKeyFindings())));
                if (containsAny(text, "supports", "improves", "benefit", "recommended", "effective")) {
                    supporting.add(source);
                }
                if (containsAny(text, "limited", "no effect", "insufficient", "inconclusive", "mixed", "uncertain")) {
                    opposing.add(source);
                }
            }
            if (!supporting.isEmpty() && !opposing.isEmpty()) {
                DietPlan.ConflictNote note = new DietPlan.ConflictNote();
                note.setTopic(entry.getKey());
                note.setSupportingSourceIds(supporting.stream().map(ExpertSource::getId).toList());
                note.setOpposingSourceIds(opposing.stream().map(ExpertSource::getId).toList());
                note.setSummary("Evidence is mixed for " + entry.getKey() + ". Some sources report benefits while others indicate limited or uncertain effects.");
                note.setResolution("Prioritize consensus and higher-level evidence while personalizing conservatively.");
                note.setResolutionBasis("Meta-analyses and systematic reviews are prioritized over single studies.");
                conflicts.add(note);
            }
        }
        return conflicts;
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
