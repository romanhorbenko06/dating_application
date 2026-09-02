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

    public User getNextProfile(Long userId, SearchCriteria criteria) {
        boolean hasMinAge = criteria.getMinAge() != null;
        boolean hasMaxAge = criteria.getMaxAge() != null;
        LocalDate bornBefore = hasMinAge
                ? LocalDate.now().minusYears(criteria.getMinAge())
                : null;
        LocalDate bornAfter = hasMaxAge
                ? LocalDate.now().minusYears(criteria.getMaxAge() + 1)
                : null;

        boolean hasDatingGoal = criteria.getDatingGoal() != null;
        boolean hasGender = criteria.getGender() != null;

        List<User> candidates = userRepository.findFeedCandidates(
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
                PageRequest.of(0, 1)
        );

        if (candidates.isEmpty()) {
            throw new BusinessException("No more profiles");
        }
        return candidates.get(0);
    }
}