package com.example.dating_application.Сontroller;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Response.NotificationResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false, defaultValue = "0")
            @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 200, message = "size must not exceed 200") int size) {

        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(notificationService.list(currentUserId, unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        Long currentUserId = getCurrentUserId();
        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", notificationService.unreadCount(currentUserId));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        Long currentUserId = getCurrentUserId();
        Map<String, Object> response = new HashMap<>();
        response.put("markedCount", notificationService.markAllRead(currentUserId));
        return ResponseEntity.ok(response);
    }
}
