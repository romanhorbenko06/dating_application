package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Response.ComplaintResponseDTO;
import com.example.dating_application.Entity.Complaint;
import com.example.dating_application.Entity.ComplaintStatus;
import com.example.dating_application.Repo.ComplaintRepository;
import com.example.dating_application.Repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    public ComplaintService(ComplaintRepository complaintRepository,
                            UserRepository userRepository) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
    }

    public Complaint create(Long fromUserId, Long reportedUserId, String reason) {

        if (reportedUserId == null) {
            throw new BusinessException("reportedUserId is required");
        }

        if (fromUserId.equals(reportedUserId)) {
            throw new BusinessException("You cannot report yourself");
        }

        Complaint c = new Complaint();
        c.setReporter(userRepository.findById(fromUserId)
                .orElseThrow(() -> new BusinessException("From user not found")));
        c.setReportedUser(userRepository.findById(reportedUserId)
                .orElseThrow(() -> new BusinessException("Reported user not found")));
        c.setReason(reason);
        c.setCreatedAt(LocalDateTime.now());
        c.setStatus(ComplaintStatus.PENDING);

        return complaintRepository.save(c);
    }

    /**
     * Скарги для адмін-панелі. status = null → усі (PENDING, REVIEWED і RESOLVED разом):
     * раніше віддавались лише PENDING, тож опрацьовані скарги неможливо було переглянути.
     */
    public List<Complaint> getComplaints(ComplaintStatus status) {
        return status == null
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Complaint markReviewed(Long complaintId) {

        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new BusinessException("Complaint not found"));

        c.setStatus(ComplaintStatus.REVIEWED);
        return complaintRepository.save(c);
    }

    public Complaint resolve(Long complaintId) {

        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new BusinessException("Complaint not found"));

        c.setStatus(ComplaintStatus.RESOLVED);
        return complaintRepository.save(c);
    }

    public List<Complaint> getByReportedUser(Long userId) {
        return complaintRepository.findByReportedUserUserIdOrderByCreatedAtDesc(userId);
    }

    public ComplaintResponseDTO toResponseDTO(Complaint c) {
        return new ComplaintResponseDTO(
                c.getComplainId(),
                c.getReporter().getUserId(),
                c.getReporter().getName(),
                c.getReportedUser().getUserId(),
                c.getReportedUser().getName(),
                c.getReason(),
                c.getStatus(),
                c.getCreatedAt()
        );
    }
}

