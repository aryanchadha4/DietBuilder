package com.dietbuilder.repository;

import com.dietbuilder.model.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    List<UserProfile> findByUserIdOrderByCreatedAtDesc(String userId);
}
