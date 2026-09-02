package com.example.dating_application.Сontroller;

import jakarta.validation.Valid;
import com.example.dating_application.DTO.Request.AdminBlockRequestDTO;
import com.example.dating_application.DTO.Response.BlockedUserDTO;
import com.example.dating_application.DTO.Response.ComplaintResponseDTO;
import com.example.dating_application.Entity.ComplaintStatus;
import com.example.dating_application.Service.ComplaintService;
import com.example.dating_application.Service.UserService;
import com.example.dating_application.Websocket.ChatWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("@accessControlService.isCurrentUserAdmin()")
public class AdminController {

    private final ComplaintService complaintService;
    private final UserService userService;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public AdminController(ComplaintService complaintService, UserService userService,
                           ChatWebSocketHandler chatWebSocketHandler) {
        this.complaintService = complaintService;
        this.userService = userService;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @GetMapping("/complaints")
    public ResponseEntity<List<ComplaintResponseDTO>> getComplaints(
            @RequestParam(required = false) ComplaintStatus status) {
        List<ComplaintResponseDTO> complaints = complaintService.getComplaints(status).stream()
                .map(complaintService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(complaints);
    }

    @GetMapping("/complaints/user/{userId}")
    public ResponseEntity<List<ComplaintResponseDTO>> getComplaintsAboutUser(@PathVariable Long userId) {
        List<ComplaintResponseDTO> complaints = complaintService.getByReportedUser(userId).stream()
                .map(complaintService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(complaints);
    }

    @PutMapping("/complaints/{id}/review")
    public ResponseEntity<ComplaintResponseDTO> reviewComplaint(@PathVariable Long id) {
        var complaint = complaintService.markReviewed(id);
        return ResponseEntity.ok(complaintService.toResponseDTO(complaint));
    }

    @PutMapping("/complaints/{id}/resolve")
    public ResponseEntity<ComplaintResponseDTO> resolveComplaint(@PathVariable Long id) {
        var complaint = complaintService.resolve(id);
        return ResponseEntity.ok(complaintService.toResponseDTO(complaint));
    }

    /** Відхилити скаргу як безпідставну (FR-21.2) — порушення не підтвердилось. */
    @PutMapping("/complaints/{id}/reject")
    public ResponseEntity<ComplaintResponseDTO> rejectComplaint(@PathVariable Long id) {
        var complaint = complaintService.reject(id);
        return ResponseEntity.ok(complaintService.toResponseDTO(complaint));
    }

    @PutMapping("/users/{id}/block")
    public ResponseEntity<BlockedUserDTO> blockUser(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) AdminBlockRequestDTO dto) {
        var user = userService.blockUserPermanently(id, dto == null ? null : dto.getReason());
        chatWebSocketHandler.disconnectUser(id);
        return ResponseEntity.ok(userService.toBlockedUserDTO(user));
    }

    @GetMapping("/users/blocked")
    public ResponseEntity<List<BlockedUserDTO>> getBlockedUsers() {
        List<BlockedUserDTO> blocked = userService.getBlockedUsers().stream()
                .map(userService::toBlockedUserDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(blocked);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User deleted by admin");
        return ResponseEntity.ok(response);
    }
}