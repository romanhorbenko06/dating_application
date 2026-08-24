package com.example.dating_application.Сontroller;

import jakarta.validation.Valid;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.ComplaintRequestDTO;
import com.example.dating_application.DTO.Response.ComplaintResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Service.ComplaintService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    /** Будь-який автентифікований користувач може поскаржитись на іншого. */
    @PostMapping
    public ResponseEntity<ComplaintResponseDTO> fileComplaint(@Valid @RequestBody ComplaintRequestDTO dto) {
        Long currentUserId = getCurrentUserId();
        var complaint = complaintService.create(currentUserId, dto.getReportedUserId(), dto.getReason());
        return ResponseEntity.ok(complaintService.toResponseDTO(complaint));
    }
}