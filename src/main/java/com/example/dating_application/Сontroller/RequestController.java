package com.example.dating_application.Сontroller;

import jakarta.validation.Valid;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Request.SendRequestDTO;
import com.example.dating_application.DTO.Response.RequestResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Service.RequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    @PostMapping("/send")
    public ResponseEntity<RequestResponseDTO> sendLike(@Valid @RequestBody SendRequestDTO dto) {
        Long currentUserId = getCurrentUserId();
        var request = requestService.send(currentUserId, dto);
        return ResponseEntity.ok(requestService.toResponseDTO(request));
    }

    @PostMapping("/{requestId}/accept")
    @PreAuthorize("@accessControlService.canAcceptRequest(#requestId)")
    public ResponseEntity<Map<String, Object>> acceptLike(@PathVariable Long requestId) {
        Long currentUserId = getCurrentUserId();
        var chat = requestService.accept(requestId, currentUserId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Like accepted and chat created");
        response.put("chatId", chat.getChatId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("@accessControlService.canRejectRequest(#requestId)")
    public ResponseEntity<Map<String, Object>> rejectLike(@PathVariable Long requestId) {
        Long currentUserId = getCurrentUserId();
        requestService.reject(requestId, currentUserId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Like rejected");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incoming")
    public ResponseEntity<List<RequestResponseDTO>> getIncomingLikes() {
        Long currentUserId = getCurrentUserId();
        var requests = requestService.incoming(currentUserId);
        List<RequestResponseDTO> dtos = requests.stream()
                .map(requestService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/sent")
    public ResponseEntity<List<RequestResponseDTO>> getSentLikes() {
        Long currentUserId = getCurrentUserId();
        var requests = requestService.sent(currentUserId);
        List<RequestResponseDTO> dtos = requests.stream()
                .map(requestService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/skip/{userId}")
    public ResponseEntity<Map<String, Object>> skipProfile(@PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        requestService.skip(currentUserId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile skipped");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/skipped")
    public ResponseEntity<List<Map<String, Object>>> getSkippedProfiles() {
        Long currentUserId = getCurrentUserId();
        var requests = requestService.getSkipped(currentUserId);
        List<Map<String, Object>> skipped = requests.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", r.getToUser().getUserId());
                    m.put("name", r.getToUser().getName());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(skipped);
    }
}