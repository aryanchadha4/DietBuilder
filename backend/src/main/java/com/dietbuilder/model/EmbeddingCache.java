package com.dietbuilder.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "embedding_cache")
public class EmbeddingCache {
    @Id
    private String id;

    private String contentHash;
    private String embeddingModel;
    private List<Double> embedding = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;
}
