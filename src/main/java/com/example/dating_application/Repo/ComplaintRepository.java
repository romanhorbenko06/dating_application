package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Complaint;
import com.example.dating_application.Entity.ComplaintStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // Скрізь свіжі — першими: адмін розбирає чергу згори
    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    List<Complaint> findByReportedUserUserIdOrderByCreatedAtDesc(Long userId);

    // Сторінкові варіанти тих самих запитів. Друге поле сортування (complainId)
    // потрібне для стабільного порядку: createdAt у двох скарг може збігтися,
    // і тоді запис міг би повторитись на сусідніх сторінках або зникнути між ними.
    List<Complaint> findAllByOrderByCreatedAtDescComplainIdDesc(Pageable pageable);

    List<Complaint> findByStatusOrderByCreatedAtDescComplainIdDesc(ComplaintStatus status, Pageable pageable);

    List<Complaint> findByReportedUserUserIdOrderByCreatedAtDescComplainIdDesc(Long userId, Pageable pageable);

    void deleteByReporterUserIdOrReportedUserUserId(Long reporterId, Long reportedUserId);
}
