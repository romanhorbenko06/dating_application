package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Response.ComplaintResponseDTO;
import com.example.dating_application.Entity.Complaint;
import com.example.dating_application.Entity.ComplaintStatus;
import com.example.dating_application.Entity.Role;
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

        var reportedUser = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new BusinessException("Reported user not found"));

        // Скарга на адміністратора не має адресата: розглядати її нікому,
        // а в черзі модерації вона лише створювала б шум.
        if (reportedUser.getRole() == Role.ADMIN) {
            throw new BusinessException("Administrators cannot be reported");
        }

        Complaint c = new Complaint();
        c.setReporter(userRepository.findById(fromUserId)
                .orElseThrow(() -> new BusinessException("From user not found")));
        c.setReportedUser(reportedUser);
        c.setReason(reason);
        c.setCreatedAt(LocalDateTime.now());
        c.setStatus(ComplaintStatus.PENDING);

        return complaintRepository.save(c);
    }

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

    /**
     * Відхилити скаргу як безпідставну (FR-21.2). На відміну від resolve,
     * означає, що порушення не підтвердилось і санкцій не буде.
     */
    public Complaint reject(Long complaintId) {

        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new BusinessException("Complaint not found"));

        c.setStatus(ComplaintStatus.REJECTED);
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

