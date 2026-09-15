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

    /**
     * Скільки тем збігається у двох людей. Одним запитом: інакше на кожен рядок
     * списку симпатій довелося б витягати обидва набори тегів і перетинати їх у пам'яті.
     * Симетрична — порядок аргументів не має значення.
     */
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