package com.dietbuilder.service;

import com.dietbuilder.model.Task;
import com.dietbuilder.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public List<Task> getAllTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    public Task getTaskById(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }

    public List<Task> getTasksByProfileId(String profileId) {
        return taskRepository.findByProfileIdOrderByCreatedAtDesc(profileId);
    }

    public Task createTask(Task task) {
        return taskRepository.save(task);
    }

    public Task updateTask(String id, Task updates) {
        Task existing = getTaskById(id);
        if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getPriority() != null) existing.setPriority(updates.getPriority());
        if (updates.getDueDate() != null) existing.setDueDate(updates.getDueDate());
        if (updates.getProfileId() != null) existing.setProfileId(updates.getProfileId());
        return taskRepository.save(existing);
    }

    public void deleteTask(String id) {
        taskRepository.deleteById(id);
    }
}
