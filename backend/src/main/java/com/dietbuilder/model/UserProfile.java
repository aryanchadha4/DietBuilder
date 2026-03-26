package com.dietbuilder.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "user_profiles")
public class UserProfile {

    @Id
    private String id;

    private String userId;

    @NotBlank(message = "Name is required")
    private String name;

    @Positive(message = "Age must be positive")
    private int age;

    private String gender;
    private String race;

    @Positive(message = "Height must be positive")
    private double heightCm;

    @Positive(message = "Weight must be positive")
    private double weightKg;

    private String preferredUnits = "METRIC";

    private List<ExerciseSchedule> strengthTraining = new ArrayList<>();
    private List<ExerciseSchedule> cardioSchedule = new ArrayList<>();

    private List<String> dietaryRestrictions = new ArrayList<>();

    private String medicalInfo = "";

    private List<String> goals = new ArrayList<>();

    private List<String> availableFoods = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    public static class ExerciseSchedule {
        private int daysPerWeek;
        private String type;
        private int durationMinutes;
    }
}
