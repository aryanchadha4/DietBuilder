package com.dietbuilder.service;

import com.dietbuilder.model.EmbeddingCache;
import com.dietbuilder.repository.EmbeddingCacheRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingCacheRepository embeddingCacheRepository;

    @Value("${rag.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    public List<Double> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String contentHash = sha256(text.trim());
        Optional<EmbeddingCache> cached = embeddingCacheRepository
                .findByContentHashAndEmbeddingModel(contentHash, embeddingModel);
        if (cached.isPresent() && cached.get().getEmbedding() != null && !cached.get().getEmbedding().isEmpty()) {
            return cached.get().getEmbedding();
        }

        List<Double> embedding = fetchEmbedding(text);
        if (!embedding.isEmpty()) {
            EmbeddingCache cache = new EmbeddingCache();
            cache.setContentHash(contentHash);
            cache.setEmbeddingModel(embeddingModel);
            cache.setEmbedding(embedding);
            embeddingCacheRepository.save(cache);
        }
        return embedding;
    }

    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<Double> fetchEmbedding(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embeddingModel);
        body.put("input", text);

        try {
            String responseJson = openAiWebClient.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode vectorNode = root.path("data").path(0).path("embedding");
            if (!vectorNode.isArray()) {
                return List.of();
            }
            List<Double> vector = new ArrayList<>();
            for (JsonNode node : vectorNode) {
                vector.add(node.asDouble());
            }
            return vector;
        } catch (Exception e) {
            log.error("Failed to fetch embedding", e);
            return List.of();
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Unable to hash embedding input", e);
        }
    }
}
