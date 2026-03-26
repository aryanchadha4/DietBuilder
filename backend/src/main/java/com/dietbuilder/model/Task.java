package com.dietbuilder.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "tasks")
public class Task {

    @Id
    private String id;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private TaskStatus status = TaskStatus.PENDING;

    private TaskPriority priority = TaskPriority.MEDIUM;

    private String dueDate;

    private String profileId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public enum TaskStatus {
        PENDING, IN_PROGRESS, COMPLETED
    }

    public enum TaskPriority {
        LOW, MEDIUM, HIGH
    }
}
