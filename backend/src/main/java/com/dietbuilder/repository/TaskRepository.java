package com.dietbuilder.repository;

import com.dietbuilder.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByProfileIdOrderByCreatedAtDesc(String profileId);
    List<Task> findByStatusOrderByCreatedAtDesc(Task.TaskStatus status);
    List<Task> findAllByOrderByCreatedAtDesc();
}
