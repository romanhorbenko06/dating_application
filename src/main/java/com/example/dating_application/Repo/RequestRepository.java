package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.RequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByToUserUserId(Long userId);

    List<Request> findByToUserUserIdAndStatus(Long userId, RequestStatus status);

    List<Request> findByFromUserUserId(Long userId);

    List<Request> findByFromUserUserIdAndStatus(Long userId, RequestStatus status);

    List<Request> findByFromUserUserIdAndStatusNot(Long userId, RequestStatus status);

    /** Відсів заблокованих і забанених у SQL, щоб не ламати пагінацію; requestId — для стабільного порядку. */
    @Query("""
            SELECT r FROM Request r
            WHERE r.toUser.userId = :userId
              AND r.status = :status
              AND r.fromUser.isBlocked = FALSE
              AND NOT EXISTS (
                  SELECT b FROM Block b
                  WHERE (b.blocker.userId = :userId AND b.blocked.userId = r.fromUser.userId)
                     OR (b.blocker.userId = r.fromUser.userId AND b.blocked.userId = :userId)
              )
            ORDER BY r.createdAt DESC, r.requestId DESC
            """)
    List<Request> findIncomingVisible(@Param("userId") Long userId,
                                      @Param("status") RequestStatus status,
                                      Pageable pageable);

    /** Накопичувальний показник для анкети: усі, хто вподобав, без пропусків, забанених і заблокованих. */
    @Query("""
            SELECT COUNT(r) FROM Request r
            WHERE r.toUser.userId = :userId
              AND r.status <> com.example.dating_application.Entity.RequestStatus.SKIPPED
              AND r.fromUser.isBlocked = FALSE
              AND NOT EXISTS (
                  SELECT b FROM Block b
                  WHERE (b.blocker.userId = :userId AND b.blocked.userId = r.fromUser.userId)
                     OR (b.blocker.userId = r.fromUser.userId AND b.blocked.userId = :userId)
              )
            """)
    long countIncomingLikes(@Param("userId") Long userId);

    List<Request> findByFromUserUserIdAndStatusNotOrderByCreatedAtDescRequestIdDesc(
            Long userId, RequestStatus status, Pageable pageable);

    List<Request> findByFromUserUserIdAndStatusOrderByCreatedAtDescRequestIdDesc(
            Long userId, RequestStatus status, Pageable pageable);

    Optional<Request> findByFromUserUserIdAndToUserUserIdAndStatus(Long fromUserId, Long toUserId, RequestStatus status);

    void deleteByFromUserUserIdOrToUserUserId(Long fromUserId, Long toUserId);
}
