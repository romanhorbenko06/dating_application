package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Complaint;
import com.example.dating_application.Entity.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // Скрізь свіжі — першими: адмін розбирає чергу згори
    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    List<Complaint> findByReportedUserUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByReporterUserIdOrReportedUserUserId(Long reporterId, Long reportedUserId);
}
