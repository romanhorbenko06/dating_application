package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByToUserUserId(Long userId);

    List<Request> findByToUserUserIdAndStatus(Long userId, RequestStatus status);

    List<Request> findByFromUserUserId(Long userId);

    List<Request> findByFromUserUserIdAndStatus(Long userId, RequestStatus status);

    List<Request> findByFromUserUserIdAndStatusNot(Long userId, RequestStatus status);

    Optional<Request> findByFromUserUserIdAndToUserUserIdAndStatus(Long fromUserId, Long toUserId, RequestStatus status);

    void deleteByFromUserUserIdOrToUserUserId(Long fromUserId, Long toUserId);
}



