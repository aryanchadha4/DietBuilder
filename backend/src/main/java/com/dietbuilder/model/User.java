package com.dietbuilder.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String username;

    @NotBlank
    @Indexed(unique = true)
    private String email;

    @JsonIgnore
    @NotBlank
    private String passwordHash;

    @CreatedDate
    private Instant createdAt;
}
