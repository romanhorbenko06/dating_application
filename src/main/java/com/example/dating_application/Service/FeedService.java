package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FeedService {

    private final UserRepository userRepository;

    public FeedService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Кілька наступних кандидатів одним запитом — для смуги «далі у стрічці».
     * Той самий добір і те саме ранжування, що й у {@link #getNextProfile},
     * лише більший розмір сторінки: перший елемент завжди дорівнює поточній анкеті.
     */
    public List<User> getUpcomingProfiles(Long userId, SearchCriteria criteria, int limit) {
        return findCandidates(userId, criteria, limit);
    }

    public User getNextProfile(Long userId, SearchCriteria criteria) {
        List<User> candidates = findCandidates(userId, criteria, 1);

        if (candidates.isEmpty()) {
            throw new BusinessException("No more profiles");
        }
        return candidates.get(0);
    }

    /** Спільний добір кандидатів: фільтри → межі дат → запит із ранжуванням за спільними тегами. */
    private List<User> findCandidates(Long userId, SearchCriteria criteria, int limit) {
        // Вік → межі дати народження (фільтрація виконується в БД):
        //   minAge → народжений НЕ ПІЗНІШЕ ніж (сьогодні - minAge) років
        //   maxAge → народжений НЕ РАНІШЕ ніж (сьогодні - (maxAge + 1)) років
        boolean hasMinAge = criteria.getMinAge() != null;
        boolean hasMaxAge = criteria.getMaxAge() != null;
        LocalDate bornBefore = hasMinAge ? LocalDate.now().minusYears(criteria.getMinAge()) : null;
        LocalDate bornAfter = hasMaxAge ? LocalDate.now().minusYears(criteria.getMaxAge() + 1) : null;

        boolean hasDatingGoal = criteria.getDatingGoal() != null;
        boolean hasGender = criteria.getGender() != null;

        return userRepository.findFeedCandidates(
                userId,
                hasGender,
                criteria.getGender(),
                criteria.getCity(),
                hasDatingGoal,
                criteria.getDatingGoal(),
                hasMinAge,
                bornBefore,
                hasMaxAge,
                bornAfter,
                PageRequest.of(0, limit)
        );
    }
}