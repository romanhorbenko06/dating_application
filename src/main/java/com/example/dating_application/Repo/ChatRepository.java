package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Chat;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findByUser1UserIdOrUser2UserId(Long user1Id, Long user2Id);

    @Query("""
        SELECT c FROM Chat c
        WHERE (c.user1.userId = :u1 AND c.user2.userId = :u2)
           OR (c.user1.userId = :u2 AND c.user2.userId = :u1)
    """)
    Chat findChatBetweenUsers(Long u1, Long u2);

    /** SELECT ... FOR UPDATE: серіалізує дії над грою в одному чаті. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Chat c WHERE c.chatId = :chatId")
    Optional<Chat> findByIdForUpdate(@Param("chatId") Long chatId);

    /** Фільтр у запиті, а не в Java, щоб сторінки були повні; найсвіжіші зверху, чати без повідомлень у кінці. */
    @Query("""
        SELECT c FROM Chat c
        WHERE (c.user1.userId = :meId
                 AND c.user2.isBlocked = FALSE
                 AND NOT EXISTS (SELECT b FROM Block b
                                  WHERE (b.blocker.userId = :meId AND b.blocked.userId = c.user2.userId)
                                     OR (b.blocker.userId = c.user2.userId AND b.blocked.userId = :meId)))
           OR (c.user2.userId = :meId
                 AND c.user1.isBlocked = FALSE
                 AND NOT EXISTS (SELECT b FROM Block b
                                  WHERE (b.blocker.userId = :meId AND b.blocked.userId = c.user1.userId)
                                     OR (b.blocker.userId = c.user1.userId AND b.blocked.userId = :meId)))
        ORDER BY (SELECT MAX(m.sentAt) FROM Message m WHERE m.chat = c) DESC NULLS LAST, c.chatId DESC
    """)
    List<Chat> findVisibleChats(@Param("meId") Long meId, Pageable pageable);
}
