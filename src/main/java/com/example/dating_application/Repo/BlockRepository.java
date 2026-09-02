package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByBlockerUserIdAndBlockedUserId(Long blockerId, Long blockedId);

    List<Block> findByBlockerUserIdOrderByCreatedAtDesc(Long blockerId);


    @Query("""
            SELECT COUNT(b) > 0 FROM Block b
            WHERE (b.blocker.userId = :userA AND b.blocked.userId = :userB)
               OR (b.blocker.userId = :userB AND b.blocked.userId = :userA)
            """)
    boolean existsBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("""
            SELECT CASE WHEN b.blocker.userId = :userId THEN b.blocked.userId ELSE b.blocker.userId END
            FROM Block b
            WHERE b.blocker.userId = :userId OR b.blocked.userId = :userId
            """)
    List<Long> findCounterpartIds(@Param("userId") Long userId);

    /** Прибирання блоків при видаленні акаунта (інакше видалення впаде на FK). */
    void deleteByBlockerUserIdOrBlockedUserId(Long blockerId, Long blockedId);
}