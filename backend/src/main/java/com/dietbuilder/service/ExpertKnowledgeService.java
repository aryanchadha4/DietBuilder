package com.dietbuilder.service;

import com.dietbuilder.model.ExpertSource;
import com.dietbuilder.model.UserProfile;
import com.dietbuilder.repository.ExpertSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpertKnowledgeService {
    private final ExpertSourceRepository expertSourceRepository;
    private final EmbeddingService embeddingService;
    private final SourceRankingService sourceRankingService;

    @Value("${rag.retrieval.top-k:15}")
    private int defaultTopK;

    @Value("${rag.retrieval.cache-ttl-minutes:60}")
    private long queryCacheTtlMinutes;

    @Value("${rag.embedding.backfill-concurrency:4}")
    private int embeddingBackfillConcurrency;

    private final Map<String, CachedQueryResult> queryCache = new LinkedHashMap<>();

    public RetrievalResult retrieveForProfile(UserProfile profile, List<String> cuisinePreferences, int topK) {
        String query = buildRetrievalQuery(profile, cuisinePreferences);
        List<SourceRankingService.RankedSource> ranked = retrieveSources(query, profile, topK > 0 ? topK : defaultTopK);
        List<ExpertSource> sources = ranked.stream().map(SourceRankingService.RankedSource::source).toList();
        double avgRelevance = sourceRankingService.computeAverageRelevance(ranked);
        return new RetrievalResult(query, ranked, sources, avgRelevance);
    }

    public String buildRetrievalQuery(UserProfile profile, List<String> cuisinePreferences) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nutrition recommendations for profile.\n");
        sb.append("Age: ").append(profile.getAge()).append("\n");
        sb.append("Gender: ").append(nullToBlank(profile.getGender())).append("\n");
        sb.append("Goals: ").append(String.join(", ", safe(profile.getGoals()))).append("\n");
        sb.append("Dietary restrictions: ").append(String.join(", ", safe(profile.getDietaryRestrictions()))).append("\n");
        sb.append("Medical conditions: ").append(nullToBlank(profile.getMedicalInfo())).append("\n");
        if (cuisinePreferences != null && !cuisinePreferences.isEmpty()) {
            sb.append("Cuisine preferences: ").append(String.join(", ", cuisinePreferences)).append("\n");
        }
        return sb.toString();
    }

    public List<SourceRankingService.RankedSource> retrieveSources(String query, UserProfile profile, int topK) {
        String cacheKey = sha256(query + "|" + topK);
        CachedQueryResult cached = queryCache.get(cacheKey);
        if (cached != null && Duration.between(cached.createdAt(), Instant.now()).toMinutes() < queryCacheTtlMinutes) {
            return cached.results();
        }

        long tQueryEmbed = System.currentTimeMillis();
        List<Double> queryEmbedding = embeddingService.getEmbedding(query);
        long queryEmbeddingMs = System.currentTimeMillis() - tQueryEmbed;
        List<ExpertSource> activeSources = expertSourceRepository.findByActiveTrue();
        List<ExpertSource> sourcesNeedingEmbedding = activeSources.stream()
                .filter(s -> s.getEmbedding() == null || s.getEmbedding().isEmpty())
                .toList();
        long backfillMs = 0;
        if (!sourcesNeedingEmbedding.isEmpty()) {
            long tBackfill = System.currentTimeMillis();
            int pool = Math.min(Math.max(1, embeddingBackfillConcurrency), sourcesNeedingEmbedding.size());
            ExecutorService executor = Executors.newFixedThreadPool(pool);
            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (ExpertSource source : sourcesNeedingEmbedding) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            String content = buildEmbeddingContent(source);
                            List<Double> embedding = embeddingService.getEmbedding(content);
                            if (!embedding.isEmpty()) {
                                source.setEmbedding(embedding);
                                expertSourceRepository.save(source);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to backfill embedding for source {}", source.getId(), e);
                        }
                    }, executor));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                executor.shutdown();
            }
            backfillMs = System.currentTimeMillis() - tBackfill;
        }
        log.info("rag.retrieval query_embedding_ms={} embedding_backfill_ms={} sources_backfilled={}",
                queryEmbeddingMs, backfillMs, sourcesNeedingEmbedding.size());

        List<SourceRankingService.RankedSource> candidates = new ArrayList<>();
        for (ExpertSource source : activeSources) {
            double similarity = embeddingService.cosineSimilarity(queryEmbedding, source.getEmbedding());
            candidates.add(new SourceRankingService.RankedSource(source, similarity, similarity));
        }

        List<SourceRankingService.RankedSource> ranked = sourceRankingService.rank(candidates, profile).stream()
                .sorted(Comparator.comparingDouble(SourceRankingService.RankedSource::compositeScore).reversed())
                .limit(Math.max(1, topK))
                .collect(Collectors.toList());

        queryCache.put(cacheKey, new CachedQueryResult(ranked, Instant.now()));
        return ranked;
    }

    public void clearQueryCache() {
        queryCache.clear();
    }

    public int evictExpiredQueryCacheEntries() {
        int before = queryCache.size();
        Instant now = Instant.now();
        queryCache.entrySet().removeIf(e ->
                Duration.between(e.getValue().createdAt(), now).toMinutes() >= queryCacheTtlMinutes);
        return Math.max(0, before - queryCache.size());
    }

    public String buildEmbeddingContent(ExpertSource source) {
        List<String> parts = new ArrayList<>();
        parts.add(nullToBlank(source.getTitle()));
        parts.add(nullToBlank(source.getSummary()));
        parts.add(nullToBlank(source.getAbstractText()));
        parts.add(String.join(", ", safe(source.getKeyFindings())));
        parts.add(String.join(", ", safe(source.getTopics())));
        parts.add(String.join(", ", safe(source.getRelevantConditions())));
        parts.add(String.join(", ", safe(source.getRelevantGoals())));
        return parts.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.joining("\n"));
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Unable to hash query", e);
        }
    }

    public record RetrievalResult(
            String retrievalQuery,
            List<SourceRankingService.RankedSource> rankedSources,
            List<ExpertSource> sources,
            double avgRelevance
    ) { }

    private record CachedQueryResult(
            List<SourceRankingService.RankedSource> results,
            Instant createdAt
    ) { }
}
