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

    /**
     * Вхідні симпатії — сторінками, найсвіжіші першими.
     *
     * Відсів заблокованих і забанених винесено в SQL з тієї ж причини, що й у
     * {@link NotificationRepository#findVisible}: у пам'яті він давав би
     * existsBetween на КОЖЕН рядок (N+1) і ламав би пагінацію — після фільтрації
     * зі сторінки лишалась би довільна кількість записів, і клієнт не міг би
     * відрізнити «сторінка неповна» від «список скінчився».
     *
     * Сортування подвійне: createdAt може збігтися, і без requestId порядок між
     * сторінками не гарантований — той самий запис міг би приїхати двічі.
     */
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

    /**
     * Скільки людей УЗАГАЛІ вподобали цього користувача — незалежно від того,
     * відповів він уже чи ні. Пропуски (SKIPPED) не рахуються: пропустити —
     * не те саме, що вподобати.
     *
     * Потрібно для плитки «вподобали вас» в анкеті: там показник накопичувальний,
     * як і сусідні «взаємні збіги». Кількість тих, хто ЧЕКАЄ відповіді, — інша
     * величина, її дає findIncomingVisible зі статусом PENDING.
     *
     * Фільтр той самий, що й у списку: забанені та заблоковані не рахуються.
     */
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

    /** Надіслані симпатії (усе, крім пропусків) — сторінками. */
    List<Request> findByFromUserUserIdAndStatusNotOrderByCreatedAtDescRequestIdDesc(
            Long userId, RequestStatus status, Pageable pageable);

    /** Пропущені профілі — сторінками. */
    List<Request> findByFromUserUserIdAndStatusOrderByCreatedAtDescRequestIdDesc(
            Long userId, RequestStatus status, Pageable pageable);

    Optional<Request> findByFromUserUserIdAndToUserUserIdAndStatus(Long fromUserId, Long toUserId, RequestStatus status);

    void deleteByFromUserUserIdOrToUserUserId(Long fromUserId, Long toUserId);
}
