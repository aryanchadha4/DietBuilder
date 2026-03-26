package com.dietbuilder.repository;

import com.dietbuilder.model.EmbeddingCache;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmbeddingCacheRepository extends MongoRepository<EmbeddingCache, String> {
    Optional<EmbeddingCache> findByContentHashAndEmbeddingModel(String contentHash, String embeddingModel);
}
