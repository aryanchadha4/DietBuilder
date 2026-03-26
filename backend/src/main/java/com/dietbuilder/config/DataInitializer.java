package com.dietbuilder.config;

import com.dietbuilder.model.FoodItem;
import com.dietbuilder.model.ExpertSource;
import com.dietbuilder.repository.FoodItemRepository;
import com.dietbuilder.repository.ExpertSourceRepository;
import com.dietbuilder.service.ExpertKnowledgeService;
import com.dietbuilder.service.EmbeddingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {
    private final FoodItemRepository foodItemRepository;
    private final ExpertSourceRepository expertSourceRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final ExpertKnowledgeService expertKnowledgeService;

    @PostConstruct
    public void init() {
        try {
            mongoTemplate.indexOps("food_items").ensureIndex(new Index().on("fdcId", Sort.Direction.ASC).unique());
            mongoTemplate.indexOps("food_items").ensureIndex(new Index().on("culturalTags", Sort.Direction.ASC));
            mongoTemplate.indexOps("expert_sources").ensureIndex(new Index().on("sourceType", Sort.Direction.ASC));
            mongoTemplate.indexOps("expert_sources").ensureIndex(new Index().on("active", Sort.Direction.ASC));
        } catch (Exception e) {
            log.warn("Index creation warning: {}", e.getMessage());
        }
        seedFoodItems();
        seedExpertSources();
    }

    private void seedFoodItems() {
        if (foodItemRepository.count() > 0) {
            log.info("Food items already populated");
            return;
        }
        try {
            InputStream is = new org.springframework.core.io.ClassPathResource("data/curated-foods.json").getInputStream();
            List<FoodItem> foods = objectMapper.readValue(is, new TypeReference<List<FoodItem>>() {});
            foodItemRepository.saveAll(foods);
            log.info("Seeded {} curated food items", foods.size());
        } catch (Exception e) {
            log.error("Failed to seed curated foods", e);
        }
    }

    private void seedExpertSources() {
        if (expertSourceRepository.count() > 0) {
            log.info("Expert sources already populated");
            return;
        }
        try {
            InputStream is = new org.springframework.core.io.ClassPathResource("data/expert-sources.json").getInputStream();
            List<ExpertSource> sources = objectMapper.readValue(is, new TypeReference<List<ExpertSource>>() {});
            for (ExpertSource source : sources) {
                String text = expertKnowledgeService.buildEmbeddingContent(source);
                source.setEmbedding(embeddingService.getEmbedding(text));
            }
            expertSourceRepository.saveAll(sources);
            log.info("Seeded {} expert sources", sources.size());
        } catch (Exception e) {
            log.error("Failed to seed expert sources", e);
        }
    }
}
