package com.example.dating_application.Repo;

import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Кандидати для стрічки, відфільтровані на рівні БД:
     * - не сам користувач
     * - лише звичайні користувачі (DATER), адміністратори у стрічку не потрапляють
     * - виключені забанені адміністратором акаунти (isBlocked = TRUE)
     * - виключені всі, з ким є блок у БУДЬ-ЯКОМУ напрямку (я заблокував / мене заблокували)
     * - виключені всі, з ким уже була взаємодія (див. нижче про обидва напрямки)
     * - опційні фільтри статі, міста, цілі та діапазону дати народження (null/false = без фільтра)
     * Ранжування: спершу за кількістю СПІЛЬНИХ тегів (інтересів) з поточним користувачем
     * (найрелевантніші — першими), далі за user_id.
     *
     * Зі стрічки випадають обидва напрямки взаємодії, а не лише мої власні лайки:
     * інакше людина, чию симпатію я щойно ПРИЙНЯВ, поверталася б у стрічку —
     * рядок Request належить їй, а не мені. Так само ховаємо тих, чий лайк я
     * відхилив, і тих, хто лайкнув мене й чекає відповіді в «Симпатіях».
     * Виняток один: їхній SKIPPED. Те, що людина мене пропустила, — її приватна
     * справа й не має викреслювати її з МОЄЇ стрічки.
     * Виклик з Pageable(0, 1) повертає лише один профіль (LIMIT 1).
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.userId <> :meId
              AND u.role = com.example.dating_application.Entity.Role.DATER
              AND u.isBlocked = FALSE
              AND NOT EXISTS (
                  SELECT r FROM Request r
                  WHERE (r.fromUser.userId = :meId AND r.toUser.userId = u.userId)
                     OR (r.fromUser.userId = u.userId AND r.toUser.userId = :meId
                         AND r.status <> com.example.dating_application.Entity.RequestStatus.SKIPPED)
              )
              AND NOT EXISTS (
                  SELECT b FROM Block b
                  WHERE (b.blocker.userId = :meId AND b.blocked.userId = u.userId)
                     OR (b.blocker.userId = u.userId AND b.blocked.userId = :meId)
              )
              AND (:hasGender = FALSE OR u.gender = :gender)
              AND (:city IS NULL OR u.city = :city)
              AND (:hasDatingGoal = FALSE OR u.datingGoal = :datingGoal)
              AND (:hasMinAge = FALSE OR u.dateOfBirth <= :bornBefore)
              AND (:hasMaxAge = FALSE OR u.dateOfBirth >= :bornAfter)
            ORDER BY (
                SELECT COUNT(ut) FROM UserTags ut
                WHERE ut.user.userId = u.userId
                  AND ut.tag.tagId IN (
                      SELECT myt.tag.tagId FROM UserTags myt WHERE myt.user.userId = :meId
                  )
            ) DESC, u.userId
            """)
    List<User> findFeedCandidates(@Param("meId") Long meId,
                                  @Param("hasGender") boolean hasGender,
                                  @Param("gender") Gender gender,
                                  @Param("city") String city,
                                  @Param("hasDatingGoal") boolean hasDatingGoal,
                                  @Param("datingGoal") DatingGoal datingGoal,
                                  @Param("hasMinAge") boolean hasMinAge,
                                  @Param("bornBefore") LocalDate bornBefore,
                                  @Param("hasMaxAge") boolean hasMaxAge,
                                  @Param("bornAfter") LocalDate bornAfter,
                                  Pageable pageable);

    /**
     * Забанені, найсвіжіші зверху, сторінкою. Порядок задає сам метод, тож
     * Pageable потрібен лише для меж — сортування в нього не передаємо.
     */
    List<User> findByIsBlockedTrueOrderByBlockedAtDesc(Pageable pageable);
}

