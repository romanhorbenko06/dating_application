package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findByUser1UserIdOrUser2UserId(Long user1Id, Long user2Id);

    @Query("""
        SELECT c FROM Chat c
        WHERE (c.user1.userId = :u1 AND c.user2.userId = :u2)
           OR (c.user1.userId = :u2 AND c.user2.userId = :u1)
    """)
    Chat findChatBetweenUsers(Long u1, Long u2);
}

