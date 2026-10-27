package com.example.dating_application.Repo;

import com.example.dating_application.Entity.UserTags;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserTagsRepository extends JpaRepository<UserTags, Long> {

    List<UserTags> findByUserUserId(Long userId);

    boolean existsByUserUserIdAndTagTagId(Long userId, Long tagId);

    void deleteByUserUserIdAndTagTagId(Long userId, Long tagId);

    @Query("""
            select count(first)
            from UserTags first, UserTags second
            where first.tag.tagId = second.tag.tagId
              and first.user.userId = :firstUserId
              and second.user.userId = :secondUserId
            """)
    long countSharedTags(@Param("firstUserId") Long firstUserId,
                         @Param("secondUserId") Long secondUserId);
}