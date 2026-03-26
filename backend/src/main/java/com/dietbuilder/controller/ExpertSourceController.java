package com.dietbuilder.controller;

import com.dietbuilder.model.ExpertSource;
import com.dietbuilder.repository.ExpertSourceRepository;
import com.dietbuilder.service.ExpertKnowledgeService;
import com.dietbuilder.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sources")
@RequiredArgsConstructor
public class ExpertSourceController {
    private final ExpertSourceRepository expertSourceRepository;
    private final EmbeddingService embeddingService;
    private final ExpertKnowledgeService expertKnowledgeService;

    @GetMapping
    public List<ExpertSource> list(
            @RequestParam(required = false) ExpertSource.SourceType sourceType,
            @RequestParam(required = false) Boolean active
    ) {
        List<ExpertSource> sources = sourceType == null
                ? expertSourceRepository.findAll()
                : expertSourceRepository.findByActiveTrueAndSourceType(sourceType);
        if (active == null) return sources;
        return sources.stream().filter(s -> s.isActive() == active).toList();
    }

    @GetMapping("/{id}")
    public ExpertSource get(@PathVariable String id) {
        return expertSourceRepository.findById(id).orElseThrow(() -> new RuntimeException("Source not found"));
    }

    @PostMapping
    public ExpertSource create(@RequestBody ExpertSource source) {
        source.setId(null);
        hydrateEmbedding(source);
        ExpertSource saved = expertSourceRepository.save(source);
        expertKnowledgeService.clearQueryCache();
        return saved;
    }

    @PutMapping("/{id}")
    public ExpertSource update(@PathVariable String id, @RequestBody ExpertSource update) {
        ExpertSource existing = get(id);
        existing.setTitle(update.getTitle());
        existing.setAuthors(update.getAuthors());
        existing.setJournal(update.getJournal());
        existing.setOrganization(update.getOrganization());
        existing.setPublicationDate(update.getPublicationDate());
        existing.setDoi(update.getDoi());
        existing.setUrl(update.getUrl());
        existing.setSourceType(update.getSourceType());
        existing.setAbstractText(update.getAbstractText());
        existing.setSummary(update.getSummary());
        existing.setKeyFindings(update.getKeyFindings());
        existing.setTopics(update.getTopics());
        existing.setRelevantConditions(update.getRelevantConditions());
        existing.setRelevantGoals(update.getRelevantGoals());
        existing.setAgeGroups(update.getAgeGroups());
        existing.setGenderRelevance(update.getGenderRelevance());
        existing.setCredibilityScore(update.getCredibilityScore());
        existing.setActive(update.isActive());
        hydrateEmbedding(existing);
        ExpertSource saved = expertSourceRepository.save(existing);
        expertKnowledgeService.clearQueryCache();
        return saved;
    }

    @DeleteMapping("/{id}")
    public void softDelete(@PathVariable String id) {
        ExpertSource source = get(id);
        source.setActive(false);
        expertSourceRepository.save(source);
        expertKnowledgeService.clearQueryCache();
    }

    @PostMapping("/bulk")
    public List<ExpertSource> bulkImport(@RequestBody List<ExpertSource> sources) {
        for (ExpertSource source : sources) {
            source.setId(null);
            hydrateEmbedding(source);
        }
        List<ExpertSource> saved = expertSourceRepository.saveAll(sources);
        expertKnowledgeService.clearQueryCache();
        return saved;
    }

    @PostMapping("/{id}/reindex")
    public ExpertSource reindex(@PathVariable String id) {
        ExpertSource source = get(id);
        hydrateEmbedding(source);
        ExpertSource saved = expertSourceRepository.save(source);
        expertKnowledgeService.clearQueryCache();
        return saved;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<ExpertSource> all = expertSourceRepository.findAll();
        long active = all.stream().filter(ExpertSource::isActive).count();
        Map<String, Long> byType = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s.getSourceType() == null ? "UNKNOWN" : s.getSourceType().name(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        double avgAgeYears = all.stream()
                .mapToInt(s -> {
                    try { return LocalDate.now().getYear() - Integer.parseInt(s.getPublicationDate().substring(0, 4)); }
                    catch (Exception ignored) { return 0; }
                })
                .average()
                .orElse(0.0);
        return Map.of(
                "totalSources", all.size(),
                "activeSources", active,
                "sourcesByType", byType,
                "averagePublicationAgeYears", avgAgeYears
        );
    }

    private void hydrateEmbedding(ExpertSource source) {
        String content = expertKnowledgeService.buildEmbeddingContent(source);
        source.setEmbedding(embeddingService.getEmbedding(content));
        if (source.getCredibilityScore() <= 0) {
            source.setCredibilityScore(0.8);
        }
    }
}
