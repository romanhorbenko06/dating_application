package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Notification;
import com.example.dating_application.Entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Видимі сповіщення користувача — сторінками, найсвіжіші першими.
     *
     * Фільтр «заблоковані та забанені» винесений у SQL навмисно: у пам'яті він давав би
     * existsBetween на КОЖНЕ сповіщення (N+1) і, головне, ламав би пагінацію —
     * зі сторінки в 50 записів після фільтрації лишалось би довільне число.
     *
     * Прапорець замість `:onlyUnread IS NULL`: у Hibernate порівняння параметра з NULL
     * ламає виведення типів (та сама пастка, що у фільтрах стрічки).
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipient.userId = :userId
              AND (:onlyUnread = FALSE OR n.isRead = FALSE)
              AND (
                  n.actor IS NULL
                  OR (
                      n.actor.isBlocked = FALSE
                      AND NOT EXISTS (
                          SELECT b FROM Block b
                          WHERE (b.blocker.userId = :userId AND b.blocked.userId = n.actor.userId)
                             OR (b.blocker.userId = n.actor.userId AND b.blocked.userId = :userId)
                      )
                  )
              )
            ORDER BY n.createdAt DESC, n.notificationId DESC
            """)
    List<Notification> findVisible(@Param("userId") Long userId,
                                   @Param("onlyUnread") boolean onlyUnread,
                                   Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipient.userId = :userId
              AND n.isRead = FALSE
              AND (
                  n.actor IS NULL
                  OR (
                      n.actor.isBlocked = FALSE
                      AND NOT EXISTS (
                          SELECT b FROM Block b
                          WHERE (b.blocker.userId = :userId AND b.blocked.userId = n.actor.userId)
                             OR (b.blocker.userId = n.actor.userId AND b.blocked.userId = :userId)
                      )
                  )
              )
            """)
    long countVisibleUnread(@Param("userId") Long userId);


    Optional<Notification> findFirstByRecipientUserIdAndChatIdAndTypeAndIsReadFalse(
            Long recipientId, Long chatId, NotificationType type);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.userId = :userId AND n.isRead = false")
    int markAllRead(@Param("userId") Long userId);

    void deleteByRecipientUserIdOrActorUserId(Long recipientId, Long actorId);
}
