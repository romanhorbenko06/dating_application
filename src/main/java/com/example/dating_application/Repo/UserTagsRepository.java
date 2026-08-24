package com.example.dating_application.Repo;

import com.example.dating_application.Entity.UserTags;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTagsRepository extends JpaRepository<UserTags, Long> {

    List<UserTags> findByUserUserId(Long userId);

    boolean existsByUserUserIdAndTagTagId(Long userId, Long tagId);

    void deleteByUserUserIdAndTagTagId(Long userId, Long tagId);
}